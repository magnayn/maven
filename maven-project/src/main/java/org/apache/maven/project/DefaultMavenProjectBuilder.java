package org.apache.maven.project;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.MavenTools;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactStatus;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.ManagedVersionMap;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.context.BuildContextManager;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.profiles.MavenProfilesBuilder;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.profiles.build.ProfileAdvisor;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.build.ProjectBuildCache;
import org.apache.maven.project.build.ProjectBuildContext;
import org.apache.maven.project.build.model.DefaultModelLineage;
import org.apache.maven.project.build.model.ModelLineage;
import org.apache.maven.project.build.model.ModelLineageBuilder;
import org.apache.maven.project.build.model.ModelLineageIterator;
import org.apache.maven.project.inheritance.ModelInheritanceAssembler;
import org.apache.maven.project.injection.ModelDefaultsInjector;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.project.validation.ModelValidator;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*:apt

 -----
 POM lifecycle
 -----

POM Lifecycle

 Order of operations when building a POM

 * inheritance
 * path translation
 * interpolation
 * defaults injection

 Current processing is:

 * inheritance
 * interpolation
 * defaults injection
 * path translation

 I'm not sure how this is working at all ... i think i have a case where this is failing but i need to
 encapsulate as a test so i can fix it. Also need to think of the in working build directory versus looking
 things up from the repository i.e buildFromSource vs buildFromRepository.

Notes

 * when the model is read it may not have a groupId, as it must be inherited

 * the inheritance assembler must use models that are unadulterated!

*/

/**
 * @version $Id$
 */
public class DefaultMavenProjectBuilder
    extends AbstractLogEnabled
    implements MavenProjectBuilder,
    Initializable
{
    protected MavenProfilesBuilder profilesBuilder;

    protected ArtifactResolver artifactResolver;

    protected ArtifactMetadataSource artifactMetadataSource;

    private ArtifactFactory artifactFactory;

    private ModelInheritanceAssembler modelInheritanceAssembler;

    private ModelValidator validator;

    // TODO: make it a component
    private MavenXpp3Reader modelReader;

    private PathTranslator pathTranslator;

    private ModelDefaultsInjector modelDefaultsInjector;

    private ModelInterpolator modelInterpolator;

    private ModelLineageBuilder modelLineageBuilder;

    private ProfileAdvisor profileAdvisor;

    private BuildContextManager buildContextManager;

    private MavenTools mavenTools;

    public static final String MAVEN_MODEL_VERSION = "4.0.0";

    public void initialize()
    {
        modelReader = new MavenXpp3Reader();
    }

    // ----------------------------------------------------------------------
    // MavenProjectBuilder Implementation
    // ----------------------------------------------------------------------

    public MavenProject build( File projectDescriptor,
                               ArtifactRepository localRepository,
                               ProfileManager profileManager )
        throws ProjectBuildingException
    {
        return buildFromSourceFileInternal( projectDescriptor, localRepository, profileManager );
    }

    /** @deprecated  */
    public MavenProject buildFromRepository( Artifact artifact,
                                             List remoteArtifactRepositories,
                                             ArtifactRepository localRepository,
                                             boolean allowStub )
        throws ProjectBuildingException

    {
        return buildFromRepository( artifact, remoteArtifactRepositories, localRepository );
    }


    public MavenProject buildFromRepository( Artifact artifact,
                                             List remoteArtifactRepositories,
                                             ArtifactRepository localRepository )
        throws ProjectBuildingException
    {
        ProjectBuildCache projectBuildCache = ProjectBuildCache.read( buildContextManager );

        MavenProject project = projectBuildCache.getCachedProject( artifact );

        if ( project != null )
        {
            return project;
        }

        Model model = findModelFromRepository( artifact, remoteArtifactRepositories, localRepository );

        return buildInternal( artifact.getFile(), model, localRepository, remoteArtifactRepositories, null, null,
                              false, false );
    }

    private MavenProject superProject;

    public MavenProject buildStandaloneSuperProject()
        throws ProjectBuildingException
    {
        if ( superProject != null )
        {
            return superProject;
        }

        Model superModel = getSuperModel();

        superModel.setGroupId( STANDALONE_SUPERPOM_GROUPID );

        superModel.setArtifactId( STANDALONE_SUPERPOM_ARTIFACTID );

        superModel.setVersion( STANDALONE_SUPERPOM_VERSION );

        superProject = new MavenProject( superModel );

        try
        {
            superProject.setRemoteArtifactRepositories( mavenTools.buildArtifactRepositories( superModel.getRepositories() ) );

            superProject.setPluginArtifactRepositories( mavenTools.buildArtifactRepositories( superModel.getRepositories() ) );
        }
        catch ( InvalidRepositoryException e )
        {
            // we shouldn't be swallowing exceptions, no matter how unlikely.
            // or, if we do, we should pay attention to the one coming from getSuperModel()...
            throw new ProjectBuildingException( STANDALONE_SUPERPOM_GROUPID + ":"
                                                + STANDALONE_SUPERPOM_ARTIFACTID,
                                                "Maven super-POM contains an invalid repository!",
                                                e );
        }

        superProject.setOriginalModel( superModel );

        superProject.setExecutionRoot( true );

        return superProject;
    }

    public MavenProjectBuildingResult buildWithDependencies( File projectDescriptor,
                                                             ArtifactRepository localRepository,
                                                             ProfileManager profileManager )
        throws ProjectBuildingException
    {
        MavenProject project = build( projectDescriptor, localRepository, profileManager );

        // ----------------------------------------------------------------------
        // Typically when the project builder is being used from maven proper
        // the transitive dependencies will not be resolved here because this
        // requires a lot of work when we may only be interested in running
        // something simple like 'm2 clean'. So the artifact collector is used
        // in the dependency resolution phase if it is required by any of the
        // goals being executed. But when used as a component in another piece
        // of code people may just want to build maven projects and have the
        // dependencies resolved for whatever reason: this is why we keep
        // this snippet of code here.
        // ----------------------------------------------------------------------

        // TODO: such a call in MavenMetadataSource too - packaging not really the intention of type
        Artifact projectArtifact = project.getArtifact();

        String projectId = safeVersionlessKey( project.getGroupId(), project.getArtifactId() );

        Map managedVersions = project.getManagedVersionMap();

        try
        {
            project.setDependencyArtifacts( project.createArtifacts( artifactFactory, null, null ) );
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new ProjectBuildingException( projectId,
                "Unable to build project due to an invalid dependency version: " +
                    e.getMessage(), projectDescriptor, e );
        }

        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
            .setArtifact( projectArtifact )
            .setArtifactDependencies( project.getDependencyArtifacts() )
            .setLocalRepository( localRepository )
            .setRemoteRepostories( project.getRemoteArtifactRepositories() )
            .setManagedVersionMap( managedVersions )
            .setMetadataSource( artifactMetadataSource );

        ArtifactResolutionResult result = artifactResolver.resolve( request );

        project.setArtifacts( result.getArtifacts() );

        return new MavenProjectBuildingResult( project, result );
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    private Map createManagedVersionMap( String projectId,
                                         DependencyManagement dependencyManagement, File pomFile )
        throws ProjectBuildingException
    {
        Map map = null;
        List deps;
        if ( ( dependencyManagement != null ) && ( ( deps = dependencyManagement.getDependencies() ) != null ) && ( deps.size() > 0 ) )
        {
            map = new ManagedVersionMap( map );

            if ( getLogger().isDebugEnabled() )
            {
                getLogger().debug( "Adding managed dependencies for " + projectId );
            }

            for ( Iterator i = dependencyManagement.getDependencies().iterator(); i.hasNext(); )
            {
                Dependency d = (Dependency) i.next();

                try
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( d.getVersion() );

                    Artifact artifact = artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(), versionRange, d.getType(),
                        d.getClassifier(), d.getScope(), d.isOptional() );

                    if ( getLogger().isDebugEnabled() )
                    {
                        getLogger().debug( "  " + artifact );
                    }

                    // If the dependencyManagement section listed exclusions,
                    // add them to the managed artifacts here so that transitive
                    // dependencies will be excluded if necessary.

                    if ( ( null != d.getExclusions() ) && !d.getExclusions().isEmpty() )
                    {
                        List exclusions = new ArrayList();

                        for ( Iterator j = d.getExclusions().iterator(); j.hasNext(); )
                        {
                            Exclusion e = (Exclusion) j.next();

                            exclusions.add( e.getGroupId() + ":" + e.getArtifactId() );
                        }

                        ExcludesArtifactFilter eaf = new ExcludesArtifactFilter( exclusions );

                        artifact.setDependencyFilter( eaf );
                    }
                    else
                    {
                        artifact.setDependencyFilter( null );
                    }

                    map.put( d.getManagementKey(), artifact );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( projectId, "Unable to parse version '" + d.getVersion() +
                        "' for dependency '" + d.getManagementKey() + "': " + e.getMessage(), pomFile, e );
                }
            }
        }
        else if ( map == null )
        {
            map = Collections.EMPTY_MAP;
        }
        return map;
    }

    private MavenProject buildFromSourceFileInternal( File projectDescriptor,
                                                      ArtifactRepository localRepository,
                                                      ProfileManager profileManager )
        throws ProjectBuildingException
    {
        /*
        // TODO: Remove this once we have build-context stuff working...
        if ( !container.getContext().contains( "SystemProperties" ) )
        {
            container.addContextValue( "SystemProperties", System.getProperties() );
        }
        */

        Model model = readModel( "unknown", projectDescriptor, STRICT_MODEL_PARSING );

        MavenProject project = buildInternal( projectDescriptor,
            model,
            localRepository,
            buildArtifactRepositories( getSuperModel() ),
            projectDescriptor,
            profileManager,
            STRICT_MODEL_PARSING,
            true );

        return project;
    }

    private Model findModelFromRepository( Artifact artifact,
                                           List remoteArtifactRepositories,
                                           ArtifactRepository localRepository )
        throws ProjectBuildingException
    {
        Artifact projectArtifact;

        // if the artifact is not a POM, we need to construct a POM artifact based on the artifact parameter given.
        if ( "pom".equals( artifact.getType() ) )
        {
            projectArtifact = artifact;
        }
        else
        {
            getLogger().warn( "Attempting to build MavenProject instance for Artifact (" + artifact.getGroupId() + ":"
                + artifact.getArtifactId() + ":" + artifact.getVersion() + ") of type: "
                + artifact.getType() + "; constructing POM artifact instead." );

            projectArtifact = artifactFactory.createProjectArtifact( artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getScope() );
        }

        Model model;

        String projectId = ArtifactUtils.versionlessKey( projectArtifact );

        try
        {
            artifactResolver.resolve( projectArtifact, remoteArtifactRepositories, localRepository );

            File file = projectArtifact.getFile();

            model = readModel( projectId, file, STRICT_MODEL_PARSING );

            String downloadUrl = null;

            ArtifactStatus status = ArtifactStatus.NONE;

            DistributionManagement distributionManagement = model.getDistributionManagement();

            if ( distributionManagement != null )
            {
                downloadUrl = distributionManagement.getDownloadUrl();

                status = ArtifactStatus.valueOf( distributionManagement.getStatus() );
            }

            checkStatusAndUpdate( projectArtifact, status, file, remoteArtifactRepositories, localRepository );

            // TODO: this is gross. Would like to give it the whole model, but maven-artifact shouldn't depend on that
            // Can a maven-core implementation of the Artifact interface store it, and be used in the exceptions?
            if ( downloadUrl != null )
            {
                projectArtifact.setDownloadUrl( downloadUrl );
            }
            else
            {
                projectArtifact.setDownloadUrl( model.getUrl() );
            }
        }
        catch ( ArtifactResolutionException e )
        {
            throw new ProjectBuildingException( projectId, "Error getting POM for '" + projectId + "' from the repository: " + e.getMessage(), e );
        }
        catch ( ArtifactNotFoundException e )
        {
            throw new ProjectBuildingException( projectId, "POM '" + projectId + "' not found in repository: " + e.getMessage(), e );
        }

        return model;
    }

    private void checkStatusAndUpdate( Artifact projectArtifact,
                                       ArtifactStatus status,
                                       File file,
                                       List remoteArtifactRepositories,
                                       ArtifactRepository localRepository )
        throws ArtifactNotFoundException
    {
        // TODO: configurable actions dependant on status
        if ( !projectArtifact.isSnapshot() && ( status.compareTo( ArtifactStatus.DEPLOYED ) < 0 ) )
        {
            // use default policy (enabled, daily update, warn on bad checksum)
            ArtifactRepositoryPolicy policy = new ArtifactRepositoryPolicy();
            // TODO: re-enable [MNG-798/865]
            policy.setUpdatePolicy( ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER );

            if ( policy.checkOutOfDate( new Date( file.lastModified() ) ) )
            {
                getLogger().info(
                    projectArtifact.getArtifactId() + ": updating metadata due to status of '" + status + "'" );
                try
                {
                    projectArtifact.setResolved( false );
                    artifactResolver.resolveAlways( projectArtifact, remoteArtifactRepositories, localRepository );
                }
                catch ( ArtifactResolutionException e )
                {
                    getLogger().warn( "Error updating POM - using existing version" );
                    getLogger().debug( "Cause", e );
                }
                catch ( ArtifactNotFoundException e )
                {
                    getLogger().warn( "Error updating POM - not found. Removing local copy." );
                    getLogger().debug( "Cause", e );
                    file.delete();
                    throw e;
                }
            }
        }
    }

    // jvz:note
    // We've got a mixture of things going in the USD and from the repository, sometimes the descriptor
    // is a real file and sometimes null which makes things confusing.
    private MavenProject buildInternal( File pomLocation,
                                        Model model,
                                        ArtifactRepository localRepository,
                                        List parentSearchRepositories,
                                        File projectDescriptor,
                                        ProfileManager externalProfileManager,
                                        boolean strict, boolean validProfilesXmlLocation )
        throws ProjectBuildingException
    {
        Model superModel = getSuperModel();

        MavenProject superProject = new MavenProject( superModel );

        String projectId = safeVersionlessKey( model.getGroupId(), model.getArtifactId() );

        List explicitlyActive;

        List explicitlyInactive;

        if ( externalProfileManager != null )
        {
            // used to trigger the caching of SystemProperties in the container context...
            try
            {
                externalProfileManager.getActiveProfiles();
            }
            catch ( ProfileActivationException e )
            {
                throw new ProjectBuildingException( projectId, "Failed to activate external profiles.", projectDescriptor, e );
            }

            explicitlyActive = externalProfileManager.getExplicitlyActivatedIds();

            explicitlyInactive = externalProfileManager.getExplicitlyDeactivatedIds();
        }
        else
        {
            explicitlyActive = Collections.EMPTY_LIST;

            explicitlyInactive = Collections.EMPTY_LIST;
        }

        superProject.setActiveProfiles( profileAdvisor.applyActivatedProfiles( superModel, projectDescriptor, explicitlyActive, explicitlyInactive, validProfilesXmlLocation ) );

        //noinspection CollectionDeclaredAsConcreteClass
        LinkedList lineage = new LinkedList();

        LinkedHashSet aggregatedRemoteWagonRepositories = collectInitialRepositories( model, superModel,
            parentSearchRepositories,
            projectDescriptor, explicitlyActive,
            explicitlyInactive,
            validProfilesXmlLocation );

        Model originalModel = ModelUtils.cloneModel( model );

        MavenProject project;

        try
        {
            project = assembleLineage( model, lineage, localRepository, projectDescriptor, aggregatedRemoteWagonRepositories, externalProfileManager, strict, validProfilesXmlLocation );
        }
        catch ( InvalidRepositoryException e )
        {
            throw new ProjectBuildingException( projectId, e.getMessage(), e );
        }

        project.setOriginalModel( originalModel );

        // we don't have to force the collision exception for superModel here, it's already been done in getSuperModel()
        MavenProject previousProject = superProject;

        Model previous = superProject.getModel();

        for ( Iterator i = lineage.iterator(); i.hasNext(); )
        {
            MavenProject currentProject = (MavenProject) i.next();

            Model current = currentProject.getModel();

            String pathAdjustment = null;

            try
            {
                pathAdjustment = previousProject.getModulePathAdjustment( currentProject );
            }
            catch ( IOException e )
            {
                getLogger().debug(
                    "Cannot determine whether " + currentProject.getId() + " is a module of " + previousProject.getId() + ". Reason: " + e.getMessage(),
                    e );
            }

            modelInheritanceAssembler.assembleModelInheritance( current, previous, pathAdjustment );

            previous = current;
            previousProject = currentProject;
        }

        // only add the super repository if it wasn't overridden by a profile or project
        List repositories = new ArrayList( aggregatedRemoteWagonRepositories );

        List superRepositories = buildArtifactRepositories( superModel );

        for ( Iterator i = superRepositories.iterator(); i.hasNext(); )
        {
            ArtifactRepository repository = (ArtifactRepository) i.next();

            if ( !repositories.contains( repository ) )
            {
                repositories.add( repository );
            }
        }

        try
        {
            project = processProjectLogic( pomLocation, project, projectDescriptor, strict );
        }
        catch ( ModelInterpolationException e )
        {
            throw new InvalidProjectModelException( projectId, e.getMessage(), pomLocation, e );
        }
        catch ( InvalidRepositoryException e )
        {
            throw new InvalidProjectModelException( projectId, e.getMessage(), pomLocation, e );
        }

        ProjectBuildCache projectBuildCache = ProjectBuildCache.read( buildContextManager );

        projectBuildCache.cacheProject( project );

        projectBuildCache.store( buildContextManager );

        if ( projectDescriptor != null )
        {
            pathTranslator.alignToBaseDirectory( project.getModel(), projectDescriptor.getParentFile() );

            Build build = project.getBuild();

            project.addCompileSourceRoot( build.getSourceDirectory() );

            project.addScriptSourceRoot( build.getScriptSourceDirectory() );

            project.addTestCompileSourceRoot( build.getTestSourceDirectory() );

            // Only track the file of a POM in the source tree
            project.setFile( projectDescriptor );
        }

        MavenProject rawParent = project.getParent();

        if ( rawParent != null )
        {
            MavenProject processedParent = projectBuildCache.getCachedProject( rawParent );

            // yeah, this null check might be a bit paranoid, but better safe than sorry...
            if ( processedParent != null )
            {
                project.setParent( processedParent );
            }
        }

        project.setManagedVersionMap( createManagedVersionMap( projectId, project.getDependencyManagement(), projectDescriptor ) );

        return project;
    }

    /*
     * Order is:
     *
     * 1. model profile repositories
     * 2. model repositories
     * 3. superModel profile repositories
     * 4. superModel repositories
     * 5. parentSearchRepositories
     */
    private LinkedHashSet collectInitialRepositories( Model model,
                                                      Model superModel,
                                                      List parentSearchRepositories,
                                                      File pomFile,
                                                      List explicitlyActive,
                                                      List explicitlyInactive,
                                                      boolean validProfilesXmlLocation )
        throws ProjectBuildingException
    {
        LinkedHashSet collected = new LinkedHashSet();

        collectInitialRepositoriesFromModel( collected, model, pomFile, explicitlyActive, explicitlyInactive, validProfilesXmlLocation );

        collectInitialRepositoriesFromModel( collected, superModel, null, explicitlyActive, explicitlyInactive, validProfilesXmlLocation );

        if ( ( parentSearchRepositories != null ) && !parentSearchRepositories.isEmpty() )
        {
            collected.addAll( parentSearchRepositories );
        }

        return collected;
    }

    private void collectInitialRepositoriesFromModel( LinkedHashSet collected,
                                                      Model model,
                                                      File pomFile,
                                                      List explicitlyActive,
                                                      List explicitlyInactive,
                                                      boolean validProfilesXmlLocation )
        throws ProjectBuildingException
    {
        Set reposFromProfiles = profileAdvisor.getArtifactRepositoriesFromActiveProfiles( model, pomFile, explicitlyActive, explicitlyInactive, validProfilesXmlLocation );

        if ( ( reposFromProfiles != null ) && !reposFromProfiles.isEmpty() )
        {
            collected.addAll( reposFromProfiles );
        }

        List modelRepos = model.getRepositories();

        if ( ( modelRepos != null ) && !modelRepos.isEmpty() )
        {
            try
            {
                collected.addAll( mavenTools.buildArtifactRepositories( modelRepos ) );
            }
            catch ( InvalidRepositoryException e )
            {
                throw new ProjectBuildingException( safeVersionlessKey( model.getGroupId(), model.getArtifactId() ),
                    "Failed to construct ArtifactRepository instances for repositories declared in: "
                        + model.getId(), e );
            }
        }
    }

    private String safeVersionlessKey( String groupId,
                                       String artifactId )
    {
        String gid = groupId;

        if ( StringUtils.isEmpty( gid ) )
        {
            gid = "unknown";
        }

        String aid = artifactId;

        if ( StringUtils.isEmpty( aid ) )
        {
            aid = "unknown";
        }

        return ArtifactUtils.versionlessKey( gid, aid );
    }

    private List buildArtifactRepositories( Model model )
        throws ProjectBuildingException
    {
        try
        {
            return mavenTools.buildArtifactRepositories( model.getRepositories() );
        }
        catch ( InvalidRepositoryException e )
        {
            String projectId = safeVersionlessKey( model.getGroupId(), model.getArtifactId() );

            throw new ProjectBuildingException( projectId, e.getMessage(), e );
        }
    }

    /**
     * @todo can this take in a model instead of a project and still be successful?
     * @todo In fact, does project REALLY need a MavenProject as a parent? Couldn't it have just a wrapper around a
     * model that supported parents which were also the wrapper so that inheritence was assembled. We don't really need
     * the resolved source roots, etc for the parent - that occurs for the parent when it is constructed independently
     * and projects are not cached or reused
     */
    private MavenProject processProjectLogic( File pomLocation,
                                              MavenProject project,
                                              File pomFile,
                                              boolean strict )
        throws ProjectBuildingException, ModelInterpolationException, InvalidRepositoryException
    {
        Model model = project.getModel();

        List activeProfiles = project.getActiveProfiles();

        // TODO: Clean this up...we're using this to 'jump' the interpolation step for model properties not expressed in XML.
        //  [BP] - Can this above comment be explained?
        // We don't need all the project methods that are added over those in the model, but we do need basedir
        // mkleint - using System.getProperties() is almost definitely bad for embedding.
        Map context = new HashMap( System.getProperties() );

        if ( pomFile != null )
        {
            context.put( "basedir", pomFile.getParentFile().getAbsolutePath() );
        }

        // TODO: this is a hack to ensure MNG-2124 can be satisfied without triggering MNG-1927
        //  MNG-1927 relies on the false assumption that ${project.build.*} evaluates to null, which occurs before
        //  MNG-2124 is fixed. The null value would leave it uninterpolated, to be handled after path translation.
        //  Until these steps are correctly sequenced, we guarantee these fields remain uninterpolated.
        context.put( "build.directory", null );
        context.put( "build.outputDirectory", null );
        context.put( "build.testOutputDirectory", null );
        context.put( "build.sourceDirectory", null );
        context.put( "build.testSourceDirectory", null );

        model = modelInterpolator.interpolate( model, context, strict );

        // interpolation is before injection, because interpolation is off-limits in the injected variables
        modelDefaultsInjector.injectDefaults( model );

        MavenProject parentProject = project.getParent();

        Model originalModel = project.getOriginalModel();

        Artifact parentArtifact = project.getParentArtifact();

        // We will return a different project object using the new model (hence the need to return a project, not just modify the parameter)
        project = new MavenProject( model );

        project.setOriginalModel( originalModel );

        project.setActiveProfiles( activeProfiles );

        // TODO: maybe not strictly correct, while we should enfore that packaging has a type handler of the same id, we don't
        Artifact projectArtifact = artifactFactory.createBuildArtifact( project.getGroupId(), project.getArtifactId(),
            project.getVersion(), project.getPackaging() );
        project.setArtifact( projectArtifact );

        project.setPluginArtifactRepositories( mavenTools.buildArtifactRepositories( model.getPluginRepositories() ) );

        DistributionManagement dm = model.getDistributionManagement();

        if ( dm != null )
        {
            ArtifactRepository repo = mavenTools.buildDeploymentArtifactRepository( dm.getRepository() );
            project.setReleaseArtifactRepository( repo );

            if ( dm.getSnapshotRepository() != null )
            {
                repo = mavenTools.buildDeploymentArtifactRepository( dm.getSnapshotRepository() );
                project.setSnapshotArtifactRepository( repo );
            }
        }

        project.setParent( parentProject );

        if ( parentProject != null )
        {
            project.setParentArtifact( parentArtifact );
        }

        // Must validate before artifact construction to make sure dependencies are good
        ModelValidationResult validationResult = validator.validate( model );

        String projectId = safeVersionlessKey( model.getGroupId(), model.getArtifactId() );

        if ( validationResult.getMessageCount() > 0 )
        {
            throw new InvalidProjectModelException( projectId, "Failed to validate POM", pomLocation,
                validationResult );
        }

        project.setRemoteArtifactRepositories(
            mavenTools.buildArtifactRepositories( model.getRepositories() ) );

        // TODO: these aren't taking active project artifacts into consideration in the reactor
        project.setPluginArtifacts( createPluginArtifacts( projectId, project.getBuildPlugins(), pomLocation ) );

        project.setReportArtifacts( createReportArtifacts( projectId, project.getReportPlugins(), pomLocation ) );

        project.setExtensionArtifacts( createExtensionArtifacts( projectId, project.getBuildExtensions(), pomLocation ) );

        return project;
    }

    /**
     * @param validProfilesXmlLocation
     * @noinspection CollectionDeclaredAsConcreteClass
     * @todo We need to find an effective way to unit test parts of this method!
     * @todo Refactor this into smaller methods with discrete purposes.
     */
    private MavenProject assembleLineage( Model model,
                                          LinkedList lineage,
                                          ArtifactRepository localRepository,
                                          File pomFile,
                                          Set aggregatedRemoteWagonRepositories,
                                          ProfileManager externalProfileManager,
                                          boolean strict, boolean validProfilesXmlLocation )
        throws ProjectBuildingException, InvalidRepositoryException
    {
        ModelLineage modelLineage = new DefaultModelLineage();

        modelLineage.setOrigin( model, pomFile, new ArrayList( aggregatedRemoteWagonRepositories ), validProfilesXmlLocation );

        modelLineageBuilder.resumeBuildingModelLineage( modelLineage, localRepository, externalProfileManager, !strict );

        ProjectBuildContext projectContext = ProjectBuildContext.getProjectBuildContext( buildContextManager, true );

        projectContext.setModelLineage( modelLineage );

        projectContext.store( buildContextManager );

        List explicitlyActive;

        List explicitlyInactive;

        if ( externalProfileManager != null )
        {
            explicitlyActive = externalProfileManager.getExplicitlyActivatedIds();

            explicitlyInactive = externalProfileManager.getExplicitlyDeactivatedIds();
        }
        else
        {
            explicitlyActive = Collections.EMPTY_LIST;
            explicitlyInactive = Collections.EMPTY_LIST;
        }

        MavenProject lastProject = null;
        for ( ModelLineageIterator it = modelLineage.lineageIterator(); it.hasNext(); )
        {
            Model currentModel = (Model) it.next();

            File currentPom = it.getPOMFile();

            MavenProject project = new MavenProject( currentModel );
            project.setFile( currentPom );

            projectContext.setCurrentProject( project );
            projectContext.store( buildContextManager );

            project.setActiveProfiles( profileAdvisor.applyActivatedProfiles( currentModel, currentPom, explicitlyActive,
                explicitlyInactive, validProfilesXmlLocation ) );

            if ( lastProject != null )
            {
                lastProject.setParent( project );

                lastProject.setParentArtifact( artifactFactory.createParentArtifact( project.getGroupId(), project
                    .getArtifactId(), project.getVersion() ) );
            }

            lineage.addFirst( project );

            lastProject = project;
        }

        MavenProject result = (MavenProject) lineage.getLast();

        if ( externalProfileManager != null )
        {
            LinkedHashSet active = new LinkedHashSet();

            List existingActiveProfiles = result.getActiveProfiles();
            if ( ( existingActiveProfiles != null ) && !existingActiveProfiles.isEmpty() )
            {
                active.addAll( existingActiveProfiles );
            }

            profileAdvisor.applyActivatedExternalProfiles( result.getModel(), pomFile, externalProfileManager );
        }

        return result;
    }

    private Model readModel( String projectId,
                             File file,
                             boolean strict )
        throws ProjectBuildingException
    {
        Reader reader = null;
        try
        {
            reader = ReaderFactory.newXmlReader( file );
            return readModel( projectId, file.toURI(), reader, strict );
        }
        catch ( FileNotFoundException e )
        {
            throw new ProjectBuildingException( projectId,
                "Could not find the model file '" + file.getAbsolutePath() + "'.", file, e );
        }
        catch ( IOException e )
        {
            throw new ProjectBuildingException( projectId, "Failed to build model from file '" +
                file.getAbsolutePath() + "'.\nError: \'" + e.getLocalizedMessage() + "\'", file, e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    private Model readModel( String projectId,
                             URI pomLocation,
                             Reader reader,
                             boolean strict )
        throws IOException, InvalidProjectModelException
    {
        String modelSource = IOUtil.toString( reader );

        if ( modelSource.indexOf( "<modelVersion>" + MAVEN_MODEL_VERSION ) < 0 )
        {
            throw new InvalidProjectModelException( projectId, "Not a v" + MAVEN_MODEL_VERSION + " POM.", pomLocation );
        }

        StringReader sReader = new StringReader( modelSource );

        try
        {
            return modelReader.read( sReader, strict );
        }
        catch ( XmlPullParserException e )
        {
            throw new InvalidProjectModelException( projectId, "Parse error reading POM. Reason: " + e.getMessage(),
                                                    pomLocation, e );
        }
    }

    private Model readModel( String projectId,
                             URL url,
                             boolean strict )
        throws ProjectBuildingException
    {
        Reader reader = null;
        URI uri = null;
        try
        {
            uri = new URI( url.toString() );
            reader = ReaderFactory.newXmlReader( url.openStream() );
            return readModel( projectId, uri, reader, strict );
        }
        catch ( IOException e )
        {
            throw new ProjectBuildingException( projectId, "Failed build model from URL \'" + url.toExternalForm() +
                "\'\nError: \'" + e.getLocalizedMessage() + "\'", uri, e );
        }
        catch ( URISyntaxException e )
        {
            throw new ProjectBuildingException( projectId, "Failed build model from URL \'" + url.toExternalForm()
                + "\'\nError: \'" + e.getLocalizedMessage() + "\'", e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    /**
     * @deprecated use {@link #createPluginArtifacts(String, List, File)}
     * @param projectId
     * @param plugins
     * @param pomLocation absolute path of pom file
     * @return
     * @throws ProjectBuildingException
     */
    protected Set createPluginArtifacts( String projectId,
                                         List plugins, String pomLocation )
        throws ProjectBuildingException
    {
        return createPluginArtifacts( projectId, plugins, new File( pomLocation ) );
    }

    /**
     * 
     * @param projectId
     * @param plugins
     * @param pomLocation pom file
     * @return
     * @throws ProjectBuildingException
     */
    protected Set createPluginArtifacts( String projectId,
                                         List plugins, File pomLocation )
        throws ProjectBuildingException
    {
        Set pluginArtifacts = new HashSet();

        for ( Iterator i = plugins.iterator(); i.hasNext(); )
        {
            Plugin p = (Plugin) i.next();

            String version;
            if ( StringUtils.isEmpty( p.getVersion() ) )
            {
                version = "RELEASE";
            }
            else
            {
                version = p.getVersion();
            }

            Artifact artifact;
            try
            {
                artifact = artifactFactory.createPluginArtifact( p.getGroupId(), p.getArtifactId(),
                    VersionRange.createFromVersionSpec( version ) );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new ProjectBuildingException( projectId, "Unable to parse version '" + version +
                    "' for plugin '" + ArtifactUtils.versionlessKey( p.getGroupId(), p.getArtifactId() ) + "': " +
                    e.getMessage(), pomLocation, e );
            }

            if ( artifact != null )
            {
                pluginArtifacts.add( artifact );
            }
        }

        return pluginArtifacts;
    }

    /**
     * @deprecated use {@link #createReportArtifacts(String, List, File)}
     * @param projectId
     * @param reports
     * @param pomLocation absolute path of pom file
     * @return
     * @throws ProjectBuildingException
     */
    protected Set createReportArtifacts( String projectId,
                                         List reports, String pomLocation )
        throws ProjectBuildingException
    {
        return createReportArtifacts( projectId, reports, new File( pomLocation ) );
    }

    // TODO: share with createPluginArtifacts?
    protected Set createReportArtifacts( String projectId,
                                         List reports, File pomLocation )
        throws ProjectBuildingException
    {
        Set pluginArtifacts = new HashSet();

        if ( reports != null )
        {
            for ( Iterator i = reports.iterator(); i.hasNext(); )
            {
                ReportPlugin p = (ReportPlugin) i.next();

                String version;
                if ( StringUtils.isEmpty( p.getVersion() ) )
                {
                    version = "RELEASE";
                }
                else
                {
                    version = p.getVersion();
                }

                Artifact artifact;
                try
                {
                    artifact = artifactFactory.createPluginArtifact( p.getGroupId(), p.getArtifactId(),
                        VersionRange.createFromVersionSpec( version ) );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( projectId, "Unable to parse version '" + version +
                        "' for report '" + ArtifactUtils.versionlessKey( p.getGroupId(), p.getArtifactId() ) + "': " +
                        e.getMessage(), pomLocation, e );
                }

                if ( artifact != null )
                {
                    pluginArtifacts.add( artifact );
                }
            }
        }

        return pluginArtifacts;
    }

    /**
     * @deprecated use {@link #createExtensionArtifacts(String, List, File)}
     * @param projectId
     * @param extensions
     * @param pomLocation absolute path of pom file
     * @return
     * @throws ProjectBuildingException
     */
    protected Set createExtensionArtifacts( String projectId,
                                            List extensions, String pomLocation )
        throws ProjectBuildingException
    {
        return createExtensionArtifacts( projectId, extensions, new File( pomLocation ) );
    }

    // TODO: share with createPluginArtifacts?
    protected Set createExtensionArtifacts( String projectId,
                                            List extensions, File pomLocation )
        throws ProjectBuildingException
    {
        Set extensionArtifacts = new HashSet();

        if ( extensions != null )
        {
            for ( Iterator i = extensions.iterator(); i.hasNext(); )
            {
                Extension ext = (Extension) i.next();

                String version;
                if ( StringUtils.isEmpty( ext.getVersion() ) )
                {
                    version = "RELEASE";
                }
                else
                {
                    version = ext.getVersion();
                }

                Artifact artifact;
                try
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( version );
                    artifact =
                        artifactFactory.createExtensionArtifact( ext.getGroupId(), ext.getArtifactId(), versionRange );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( projectId, "Unable to parse version '" + version +
                        "' for extension '" + ArtifactUtils.versionlessKey( ext.getGroupId(), ext.getArtifactId() ) +
                        "': " + e.getMessage(), pomLocation, e );
                }

                if ( artifact != null )
                {
                    extensionArtifacts.add( artifact );
                }
            }
        }

        return extensionArtifacts;
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    private Model getSuperModel()
        throws ProjectBuildingException
    {
        URL url = DefaultMavenProjectBuilder.class.getResource( "pom-" + MAVEN_MODEL_VERSION + ".xml" );

        String projectId = safeVersionlessKey( STANDALONE_SUPERPOM_GROUPID, STANDALONE_SUPERPOM_ARTIFACTID );

        return readModel( projectId, url, STRICT_MODEL_PARSING );
    }
}
