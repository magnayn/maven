package org.apache.maven.project;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactStatus;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.transform.ArtifactTransformationManager;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.MavenProfilesBuilder;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.profiles.ProfilesConversionUtils;
import org.apache.maven.profiles.ProfilesRoot;
import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.project.inheritance.ModelInheritanceAssembler;
import org.apache.maven.project.injection.ModelDefaultsInjector;
import org.apache.maven.project.injection.ProfileInjector;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.project.validation.ModelValidator;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @version $Id: DefaultMavenProjectBuilder.java,v 1.37 2005/03/08 01:55:22
 *          trygvis Exp $
 */
public class DefaultMavenProjectBuilder
    extends AbstractLogEnabled
    implements MavenProjectBuilder, Initializable, Contextualizable
{
    // TODO: remove
    private PlexusContainer container;

    protected MavenProfilesBuilder profilesBuilder;

    protected ArtifactResolver artifactResolver;

    protected ArtifactMetadataSource artifactMetadataSource;

    private ArtifactFactory artifactFactory;

    private ModelInheritanceAssembler modelInheritanceAssembler;

    private ProfileInjector profileInjector;

    private ModelValidator validator;

    // TODO: make it a component
    private MavenXpp3Reader modelReader;

    private PathTranslator pathTranslator;

    private ModelDefaultsInjector modelDefaultsInjector;

    private ModelInterpolator modelInterpolator;

    private ArtifactRepositoryFactory artifactRepositoryFactory;

    private ArtifactTransformationManager transformationManager;

    private final Map modelCache = new HashMap();

    public static final String MAVEN_MODEL_VERSION = "4.0.0";

    private Map projectCache = new HashMap();

    public void initialize()
    {
        modelReader = new MavenXpp3Reader();
    }

    // ----------------------------------------------------------------------
    // MavenProjectBuilder Implementation
    // ----------------------------------------------------------------------

    /**
     * @todo move to metadatasource itself?
     */
    public MavenProject buildWithDependencies( File projectDescriptor, ArtifactRepository localRepository,
                                               ProfileManager profileManager )
        throws ProjectBuildingException, ArtifactResolutionException
    {
        MavenProject project = buildFromSourceFile( projectDescriptor, localRepository, profileManager );

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

        Map managedVersions = createManagedVersionMap( project.getDependencyManagement() );

        ensureMetadataSourceIsInitialized();

        try
        {
            project.setDependencyArtifacts( project.createArtifacts( artifactFactory, null, null ) );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new ProjectBuildingException( "Error in dependency version", e );
        }
        ArtifactResolutionResult result = artifactResolver.resolveTransitively( project.getDependencyArtifacts(),
                                                                                projectArtifact, managedVersions,
                                                                                localRepository,
                                                                                project.getRemoteArtifactRepositories(),
                                                                                artifactMetadataSource );

        project.setArtifacts( result.getArtifacts() );
        return project;
    }

    private void ensureMetadataSourceIsInitialized()
        throws ProjectBuildingException
    {
        if ( artifactMetadataSource == null )
        {
            try
            {
                artifactMetadataSource = (ArtifactMetadataSource) container.lookup( ArtifactMetadataSource.ROLE );
            }
            catch ( ComponentLookupException e )
            {
                throw new ProjectBuildingException( "Cannot lookup metadata source for building the project.", e );
            }
        }
    }

    private Map createManagedVersionMap( DependencyManagement dependencyManagement )
        throws ProjectBuildingException
    {
        Map map;
        if ( dependencyManagement != null && dependencyManagement.getDependencies() != null )
        {
            map = new HashMap();
            for ( Iterator i = dependencyManagement.getDependencies().iterator(); i.hasNext(); )
            {
                Dependency d = (Dependency) i.next();

                try
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( d.getVersion() );
                    Artifact artifact = artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(),
                                                                                  versionRange, d.getType(),
                                                                                  d.getClassifier(), d.getScope() );
                    map.put( d.getManagementKey(), artifact );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( "Unable to parse dependency version", e );
                }
            }
        }
        else
        {
            map = Collections.EMPTY_MAP;
        }
        return map;
    }

    /**
     * @deprecated Use build( File, ArtifactRepository, ProfileManager)
     */
    public MavenProject build( File projectDescriptor, ArtifactRepository localRepository, List activeExternalProfiles )
        throws ProjectBuildingException
    {
        ProfileManager profileManager = new DefaultProfileManager( container );

        if ( activeExternalProfiles != null )
        {
            for ( Iterator it = activeExternalProfiles.iterator(); it.hasNext(); )
            {
                Profile profile = (Profile) it.next();

                // since it's already determined to be active, we'll explicitly set it as activated in the mgr.
                profileManager.explicitlyActivate( profile.getId() );

                profileManager.addProfile( profile );
            }
        }

        return buildFromSourceFile( projectDescriptor, localRepository, profileManager );
    }

    public MavenProject build( File projectDescriptor, ArtifactRepository localRepository,
                               ProfileManager profileManager )
        throws ProjectBuildingException
    {
        return buildFromSourceFile( projectDescriptor, localRepository, profileManager );
    }

    private MavenProject buildFromSourceFile( File projectDescriptor, ArtifactRepository localRepository,
                                              ProfileManager profileManager )
        throws ProjectBuildingException
    {
        Model model = readModel( projectDescriptor );

        // Always cache files in the source tree over those in the repository
        modelCache.put( createCacheKey( model.getGroupId(), model.getArtifactId(), model.getVersion() ), model );

        MavenProject project = build( projectDescriptor.getAbsolutePath(), model, localRepository,
                                      buildArtifactRepositories( getSuperModel() ),
                                      projectDescriptor.getAbsoluteFile().getParentFile(), profileManager );

        if ( project.getDistributionManagement() != null && project.getDistributionManagement().getStatus() != null )
        {
            throw new ProjectBuildingException(
                "Invalid project file: distribution status must not be specified for a project outside of the repository" );
        }

        // Only translate the base directory for files in the source tree
        pathTranslator.alignToBaseDirectory( project.getModel(), projectDescriptor );

        Build build = project.getBuild();
        project.addCompileSourceRoot( build.getSourceDirectory() );
        project.addScriptSourceRoot( build.getScriptSourceDirectory() );
        project.addTestCompileSourceRoot( build.getTestSourceDirectory() );

        // Only track the file of a POM in the source tree
        project.setFile( projectDescriptor );

        return project;
    }

    public MavenProject buildFromRepository( Artifact artifact, List remoteArtifactRepositories,
                                             ArtifactRepository localRepository )
        throws ProjectBuildingException
    {
        String cacheKey = createCacheKey( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );
        MavenProject project = (MavenProject) projectCache.get( cacheKey );
        if ( project != null )
        {
            return project;
        }

        Model model = findModelFromRepository( artifact, remoteArtifactRepositories, localRepository );

        return build( "Artifact [" + artifact.getId() + "]", model, localRepository, remoteArtifactRepositories, null,
                      null );
    }

    private Model findModelFromRepository( Artifact artifact, List remoteArtifactRepositories,
                                           ArtifactRepository localRepository )
        throws ProjectBuildingException
    {
        Model model = getCachedModel( artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion() );
        if ( model == null )
        {
            // TODO: can't assume artifact is a POM
            try
            {
                artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );

                File file = artifact.getFile();
                model = readModel( file );

                String downloadUrl = null;
                ArtifactStatus status = ArtifactStatus.NONE;

                DistributionManagement distributionManagement = model.getDistributionManagement();
                if ( distributionManagement != null )
                {
                    downloadUrl = distributionManagement.getDownloadUrl();

                    status = ArtifactStatus.valueOf( distributionManagement.getStatus() );
                }

                // TODO: configurable actions dependant on status
                if ( !artifact.isSnapshot() && status.compareTo( ArtifactStatus.VERIFIED ) < 0 )
                {
                    // use default policy (enabled, daily update, warn on bad checksum)
                    ArtifactRepositoryPolicy policy = new ArtifactRepositoryPolicy();

                    if ( policy.checkOutOfDate( new Date( file.lastModified() ) ) )
                    {
                        getLogger().info(
                            artifact.getArtifactId() + ": updating metadata due to status of '" + status + "'" );
                        try
                        {
                            artifact.setFile( null );
                            artifactResolver.resolveAlways( artifact, remoteArtifactRepositories, localRepository );
                        }
                        catch ( ArtifactResolutionException e )
                        {
                            getLogger().warn( "Error updating POM - using existing version" );
                            getLogger().debug( "Cause", e );
                        }
                    }
                }

                // TODO: this is gross. Would like to give it the whole model, but maven-artifact shouldn't depend on that
                // Can a maven-core implementation of the Artifact interface store it, and be used in the exceptions?
                if ( downloadUrl != null )
                {
                    artifact.setDownloadUrl( downloadUrl );
                }
                else
                {
                    artifact.setDownloadUrl( model.getUrl() );
                }

            }
            catch ( ArtifactResolutionException e )
            {
                // TODO: a not found would be better vs other errors
                // only not found should have the below behaviour
//                throw new ProjectBuildingException( "Unable to find the POM in the repository", e );

                getLogger().warn( "\n  ***** Using defaults for missing POM " + artifact.getId() + " *****\n" );

                model = new Model();
                model.setModelVersion( "4.0.0" );
                model.setArtifactId( artifact.getArtifactId() );
                model.setGroupId( artifact.getGroupId() );
                model.setVersion( artifact.getVersion() );
                // TODO: not correct in some instances
                model.setPackaging( artifact.getType() );

                model.setDistributionManagement( new DistributionManagement() );
                model.getDistributionManagement().setStatus( ArtifactStatus.GENERATED.toString() );

/* TODO: we should only do this if we can verify the existence of the JAR itself
                File file = artifact.getFile();
                file.getParentFile().mkdirs();

                FileWriter writer = null;
                try
                {
                    writer = new FileWriter( file );

                    MavenXpp3Writer w = new MavenXpp3Writer();
                    w.write( writer, model );
                }
                catch ( IOException ioe )
                {
                    getLogger().warn( "Attempted to write out a temporary generated POM, but failed", ioe );
                }
                finally
                {
                    IOUtil.close( writer );
                }
*/
            }
        }

        return model;
    }

    private MavenProject build( String pomLocation, Model model, ArtifactRepository localRepository,
                                List parentSearchRepositories, File projectDir, ProfileManager profileManager )
        throws ProjectBuildingException
    {
        Model superModel = getSuperModel();

        ProfileManager superProjectProfileManager = new DefaultProfileManager( container );

        List activeProfiles;

        Properties profileProperties = new Properties();

        superProjectProfileManager.addProfiles( superModel.getProfiles() );

        activeProfiles = injectActiveProfiles( superProjectProfileManager, superModel, profileProperties );

        MavenProject superProject = new MavenProject( superModel );

        superProject.addProfileProperties( profileProperties );

        superProject.setActiveProfiles( activeProfiles );

        //noinspection CollectionDeclaredAsConcreteClass
        LinkedList lineage = new LinkedList();

        // TODO: the aRWR can get out of sync with project.model.repositories. We should do all the processing of
        // profiles, etc on the models then recreate the aggregated sets at the end from the project repositories (they
        // must still be created along the way so that parent poms can be discovered, however)
        Set aggregatedRemoteWagonRepositories = new HashSet();

        List activeExternalProfiles;
        try
        {
            if ( profileManager != null )
            {
                activeExternalProfiles = profileManager.getActiveProfiles();
            }
            else
            {
                activeExternalProfiles = Collections.EMPTY_LIST;
            }
        }
        catch ( ProfileActivationException e )
        {
            throw new ProjectBuildingException( "Failed to calculate active external profiles.", e );
        }

        for ( Iterator i = activeExternalProfiles.iterator(); i.hasNext(); )
        {
            Profile externalProfile = (Profile) i.next();

            for ( Iterator repoIterator = externalProfile.getRepositories().iterator(); repoIterator.hasNext(); )
            {
                Repository mavenRepo = (Repository) repoIterator.next();

                ArtifactRepository artifactRepo = ProjectUtils.buildArtifactRepository( mavenRepo,
                                                                                        artifactRepositoryFactory,
                                                                                        container );

                aggregatedRemoteWagonRepositories.add( artifactRepo );
            }
        }

        Model originalModel = ModelUtils.cloneModel( model );

        MavenProject project = assembleLineage( model, lineage, localRepository, projectDir, parentSearchRepositories,
                                                aggregatedRemoteWagonRepositories );

        project.setOriginalModel( originalModel );

        // we don't have to force the collision exception for superModel here, it's already been done in getSuperModel()
        Model previous = superProject.getModel();

        for ( Iterator i = lineage.iterator(); i.hasNext(); )
        {
            MavenProject currentProject = (MavenProject) i.next();

            Model current = currentProject.getModel();

            modelInheritanceAssembler.assembleModelInheritance( current, previous );

            previous = current;
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
            project = processProjectLogic( pomLocation, project, repositories, profileManager );
        }
        catch ( ModelInterpolationException e )
        {
            throw new ProjectBuildingException( "Error building project from \'" + pomLocation + "\': " + model.getId(),
                                                e );
        }
        projectCache.put( createCacheKey( project.getGroupId(), project.getArtifactId(), project.getVersion() ),
                          project );
        return project;
    }

    private List buildArtifactRepositories( Model model )
        throws ProjectBuildingException
    {
        return ProjectUtils.buildArtifactRepositories( model.getRepositories(), artifactRepositoryFactory, container );
    }

    /**
     * @todo can this take in a model instead of a project and still be successful?
     * @todo In fact, does project REALLY need a MavenProject as a parent? Couldn't it have just a wrapper around a
     * model that supported parents which were also the wrapper so that inheritence was assembled. We don't really need
     * the resolved source roots, etc for the parent - that occurs for the parent when it is constructed independently
     * and projects are not cached or reused
     */
    private MavenProject processProjectLogic( String pomLocation, MavenProject project, List remoteRepositories,
                                              ProfileManager profileMgr )
        throws ProjectBuildingException, ModelInterpolationException
    {
        Model model = project.getModel();
        String key = createCacheKey( model.getGroupId(), model.getArtifactId(), model.getVersion() );
        Model cachedModel = (Model) modelCache.get( key );
        if ( cachedModel == null )
        {
            modelCache.put( key, model );
        }

        Properties profileProperties = project.getProfileProperties();

        if ( profileProperties == null )
        {
            profileProperties = new Properties();
        }

        List activeProfiles = project.getActiveProfiles();

        if ( activeProfiles == null )
        {
            activeProfiles = new ArrayList();
        }

        List injectedProfiles = injectActiveProfiles( profileMgr, model, profileProperties );

        activeProfiles.addAll( injectedProfiles );

        // TODO: Clean this up...we're using this to 'jump' the interpolation step for model properties not expressed in XML.
        //  [BP] - Can this above comment be explained?
        // We don't need all the project methods that are added over those in the model, but we do need basedir
        Map context = Collections.singletonMap( "basedir", project.getBasedir() );
        model = modelInterpolator.interpolate( model, context );

        // interpolation is before injection, because interpolation is off-limits in the injected variables
        modelDefaultsInjector.injectDefaults( model );

        MavenProject parentProject = project.getParent();

        Model originalModel = project.getOriginalModel();

        // We will return a different project object using the new model (hence the need to return a project, not just modify the parameter)
        project = new MavenProject( model );

        project.setOriginalModel( originalModel );

        project.setActiveProfiles( activeProfiles );

        project.addProfileProperties( profileProperties );

        project.assembleProfilePropertiesInheritance();

        // TODO: maybe not strictly correct, while we should enfore that packaging has a type handler of the same id, we don't
        Artifact projectArtifact = artifactFactory.createBuildArtifact( project.getGroupId(), project.getArtifactId(),
                                                                        project.getVersion(), project.getPackaging() );
        project.setArtifact( projectArtifact );

        if ( projectArtifact.isSnapshot() )
        {
            project.setSnapshotDeploymentVersion(
                transformationManager.getSnapshotDeploymentVersion( projectArtifact ) );

            project.setSnapshotDeploymentBuildNumber(
                transformationManager.getSnapshotDeploymentBuildNumber( projectArtifact ) );
        }

        project.setPluginArtifactRepositories( ProjectUtils.buildArtifactRepositories( model.getPluginRepositories(),
                                                                                       artifactRepositoryFactory,
                                                                                       container ) );

        DistributionManagement dm = model.getDistributionManagement();
        if ( dm != null )
        {
            ArtifactRepository repo = ProjectUtils.buildArtifactRepositoryBase( dm.getRepository(),
                                                                                artifactRepositoryFactory, container );
            project.setReleaseArtifactRepository( repo );

            if ( dm.getSnapshotRepository() != null )
            {
                repo = ProjectUtils.buildArtifactRepositoryBase( dm.getSnapshotRepository(), artifactRepositoryFactory,
                                                                 container );
                project.setSnapshotArtifactRepository( repo );
            }
        }

        project.setParent( parentProject );

        if ( parentProject != null )
        {
            Artifact parentArtifact = artifactFactory.createParentArtifact( parentProject.getGroupId(),
                                                                            parentProject.getArtifactId(),
                                                                            parentProject.getVersion() );
            project.setParentArtifact( parentArtifact );
        }

        // Must validate before artifact construction to make sure dependencies are good
        ModelValidationResult validationResult = validator.validate( model );

        if ( validationResult.getMessageCount() > 0 )
        {
            throw new ProjectBuildingException( "Failed to validate POM for \'" + pomLocation +
                "\'.\n\n  Reason(s):\n" + validationResult.render( "  " ) );
        }

        project.setRemoteArtifactRepositories( remoteRepositories );
        project.setPluginArtifacts( createPluginArtifacts( project.getBuildPlugins() ) );
        project.setReportArtifacts( createReportArtifacts( project.getReportPlugins() ) );
        project.setExtensionArtifacts( createExtensionArtifacts( project.getBuildExtensions() ) );

        return project;
    }

    /**
     * @noinspection CollectionDeclaredAsConcreteClass
     */
    private MavenProject assembleLineage( Model model, LinkedList lineage, ArtifactRepository localRepository,
                                          File projectDir, List parentSearchRepositories,
                                          Set aggregatedRemoteWagonRepositories )
        throws ProjectBuildingException
    {
        if ( !model.getRepositories().isEmpty() )
        {
            List respositories = buildArtifactRepositories( model );

            for ( Iterator it = respositories.iterator(); it.hasNext(); )
            {
                ArtifactRepository repository = (ArtifactRepository) it.next();

                if ( !aggregatedRemoteWagonRepositories.contains( repository ) )
                {
                    aggregatedRemoteWagonRepositories.add( repository );
                }
            }
        }

        ProfileManager profileManager = new DefaultProfileManager( container );

        List activeProfiles;

        Properties profileProperties = new Properties();

        try
        {
            profileManager.addProfiles( model.getProfiles() );

            loadProjectExternalProfiles( profileManager, projectDir );

            activeProfiles = injectActiveProfiles( profileManager, model, profileProperties );
        }
        catch ( ProfileActivationException e )
        {
            throw new ProjectBuildingException( "Failed to activate local (project-level) build profiles.", e );
        }

        MavenProject project = new MavenProject( model );

        project.addProfileProperties( profileProperties );

        project.setActiveProfiles( activeProfiles );

        lineage.addFirst( project );

        Parent parentModel = model.getParent();

        if ( parentModel != null )
        {
            if ( StringUtils.isEmpty( parentModel.getGroupId() ) )
            {
                throw new ProjectBuildingException( "Missing groupId element from parent element" );
            }
            else if ( StringUtils.isEmpty( parentModel.getArtifactId() ) )
            {
                throw new ProjectBuildingException( "Missing artifactId element from parent element" );
            }
            else if ( StringUtils.isEmpty( parentModel.getVersion() ) )
            {
                throw new ProjectBuildingException( "Missing version element from parent element" );
            }

            model = getCachedModel( parentModel.getGroupId(), parentModel.getArtifactId(), parentModel.getVersion() );

            // the only way this will have a value is if we find the parent on disk...
            File parentProjectDir = null;

            String parentRelativePath = parentModel.getRelativePath();

            // if we can't find a cached model matching the parent spec, then let's try to look on disk using
            // <relativePath/>
            if ( model == null && projectDir != null && StringUtils.isNotEmpty( parentRelativePath ) )
            {
                File parentDescriptor = new File( projectDir, parentRelativePath );

                try
                {
                    parentDescriptor = parentDescriptor.getCanonicalFile();
                }
                catch ( IOException e )
                {
                    getLogger().debug( "Failed to canonicalize potential parent POM: \'" + parentDescriptor + "\'", e );

                    parentDescriptor = null;
                }

                if ( parentDescriptor != null && parentDescriptor.exists() )
                {
                    Model candidateParent = readModel( parentDescriptor );

                    // this works because parent-version is still required...
                    if ( parentModel.getGroupId().equals( candidateParent.getGroupId() ) &&
                        parentModel.getArtifactId().equals( candidateParent.getArtifactId() ) && (
                        parentModel.getVersion().equals( candidateParent.getVersion() ) || (
                            candidateParent.getParent() != null &&
                                parentModel.getVersion().equals( candidateParent.getParent().getVersion() ) ) ) )
                    {
                        model = candidateParent;

                        parentProjectDir = parentDescriptor.getParentFile();

                        getLogger().debug( "Using parent-POM from the project hierarchy at: \'" +
                            parentModel.getRelativePath() + "\' for project: " + project.getId() );
                    }
                    else
                    {
                        getLogger().debug( "Invalid parent-POM referenced by relative path: \'" +
                            parentModel.getRelativePath() + "\'. It did not match parent specification in " +
                            project.getId() );
                    }
                }
            }

            Artifact parentArtifact = null;

            // only resolve the parent model from the repository system if we didn't find it on disk...
            if ( model == null )
            {
                //!! (**)
                // ----------------------------------------------------------------------
                // Do we have the necessary information to actually find the parent
                // POMs here?? I don't think so ... Say only one remote repository is
                // specified and that is ibiblio then this model that we just read doesn't
                // have any repository information ... I think we might have to inherit
                // as we go in order to do this.
                // ----------------------------------------------------------------------

                parentArtifact = artifactFactory.createParentArtifact( parentModel.getGroupId(),
                                                                       parentModel.getArtifactId(),
                                                                       parentModel.getVersion() );

                // we must add the repository this POM was found in too, by chance it may be located where the parent is
                // we can't query the parent to ask where it is :)
                List remoteRepositories = new ArrayList( aggregatedRemoteWagonRepositories );
                remoteRepositories.addAll( parentSearchRepositories );
                model = findModelFromRepository( parentArtifact, remoteRepositories, localRepository );
            }

            MavenProject parent = assembleLineage( model, lineage, localRepository, parentProjectDir,
                                                   parentSearchRepositories, aggregatedRemoteWagonRepositories );

            project.setParent( parent );

            project.setParentArtifact( parentArtifact );
        }

        return project;
    }

    private List injectActiveProfiles( ProfileManager profileManager, Model model, Properties profileProperties )
        throws ProjectBuildingException
    {
        List activeProfiles;

        if ( profileManager != null )
        {
            try
            {
                activeProfiles = profileManager.getActiveProfiles();
            }
            catch ( ProfileActivationException e )
            {
                throw new ProjectBuildingException( "Failed to calculate active build profiles.", e );
            }

            for ( Iterator it = activeProfiles.iterator(); it.hasNext(); )
            {
                Profile profile = (Profile) it.next();

                profileInjector.inject( profile, model );

                profileProperties.putAll( profile.getProperties() );
            }
        }
        else
        {
            activeProfiles = Collections.EMPTY_LIST;
        }

        return activeProfiles;
    }

    private void loadProjectExternalProfiles( ProfileManager profileManager, File projectDir )
        throws ProfileActivationException
    {
        if ( projectDir != null )
        {
            try
            {
                ProfilesRoot root = profilesBuilder.buildProfiles( projectDir );

                if ( root != null )
                {
                    for ( Iterator it = root.getProfiles().iterator(); it.hasNext(); )
                    {
                        org.apache.maven.profiles.Profile rawProfile = (org.apache.maven.profiles.Profile) it.next();

                        profileManager.addProfile( ProfilesConversionUtils.convertFromProfileXmlProfile( rawProfile ) );
                    }
                }
            }
            catch ( IOException e )
            {
                throw new ProfileActivationException( "Cannot read profiles.xml resource from directory: " + projectDir,
                                                      e );
            }
            catch ( XmlPullParserException e )
            {
                throw new ProfileActivationException(
                    "Cannot parse profiles.xml resource from directory: " + projectDir, e );
            }
        }
    }

    private Model readModel( File file )
        throws ProjectBuildingException
    {
        Reader reader = null;
        try
        {
            reader = new FileReader( file );
            return readModel( reader );
        }
        catch ( FileNotFoundException e )
        {
            throw new ProjectBuildingException( "Could not find the model file '" + file.getAbsolutePath() + "'.", e );
        }
        catch ( IOException e )
        {
            throw new ProjectBuildingException( "Failed to build model from file '" + file.getAbsolutePath() +
                "'.\nError: \'" + e.getLocalizedMessage() + "\'", e );
        }
        catch ( XmlPullParserException e )
        {
            throw new ProjectBuildingException( "Failed to parse model from file '" + file.getAbsolutePath() +
                "'.\nError: \'" + e.getLocalizedMessage() + "\'", e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    private Model readModel( Reader reader )
        throws IOException, XmlPullParserException, InvalidModelException
    {
        StringWriter sw = new StringWriter();

        IOUtil.copy( reader, sw );

        String modelSource = sw.toString();

        if ( modelSource.indexOf( "<modelVersion>4.0.0" ) < 0 )
        {
            throw new InvalidModelException( "Invalid POM (not v4.0.0 modelVersion)" );
        }

        StringReader sReader = new StringReader( modelSource );

        return modelReader.read( sReader );
    }

    private Model readModel( URL url )
        throws ProjectBuildingException
    {
        InputStreamReader reader = null;
        try
        {
            reader = new InputStreamReader( url.openStream() );
            return readModel( reader );
        }
        catch ( IOException e )
        {
            throw new ProjectBuildingException( "Failed build model from URL \'" + url.toExternalForm() +
                "\'\nError: \'" + e.getLocalizedMessage() + "\'", e );
        }
        catch ( XmlPullParserException e )
        {
            throw new ProjectBuildingException( "Failed to parse model from URL \'" + url.toExternalForm() +
                "\'\nError: \'" + e.getLocalizedMessage() + "\'", e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    private Model getCachedModel( String groupId, String artifactId, String version )
    {
        return (Model) modelCache.get( createCacheKey( groupId, artifactId, version ) );
    }

    private static String createCacheKey( String groupId, String artifactId, String version )
    {
        return groupId + ":" + artifactId + ":" + version;
    }

    protected Set createPluginArtifacts( List plugins )
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
                throw new ProjectBuildingException( "Unable to parse plugin version", e );
            }

            if ( artifact != null )
            {
                pluginArtifacts.add( artifact );
            }
        }

        return pluginArtifacts;
    }

    protected Set createReportArtifacts( List reports )
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
                    throw new ProjectBuildingException( "Unable to parse plugin version", e );
                }

                if ( artifact != null )
                {
                    pluginArtifacts.add( artifact );
                }
            }
        }

        return pluginArtifacts;
    }

    protected Set createExtensionArtifacts( List extensions )
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
                    artifact = artifactFactory.createExtensionArtifact( ext.getGroupId(), ext.getArtifactId(),
                                                                        VersionRange
                                                                            .createFromVersionSpec( version ) );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( "Unable to parse extension version", e );
                }

                if ( artifact != null )
                {
                    extensionArtifacts.add( artifact );
                }
            }
        }

        return extensionArtifacts;
    }

    public MavenProject buildStandaloneSuperProject( ArtifactRepository localRepository )
        throws ProjectBuildingException
    {
        Model superModel = getSuperModel();

        superModel.setGroupId( STANDALONE_SUPERPOM_GROUPID );

        superModel.setArtifactId( STANDALONE_SUPERPOM_ARTIFACTID );

        superModel.setVersion( STANDALONE_SUPERPOM_VERSION );

        ProfileManager profileManager = new DefaultProfileManager( container );

        List activeProfiles;

        Properties profileProperties = new Properties();

        profileManager.addProfiles( superModel.getProfiles() );

        activeProfiles = injectActiveProfiles( profileManager, superModel, profileProperties );

        MavenProject project = new MavenProject( superModel );

        project.addProfileProperties( profileProperties );

        project.setActiveProfiles( activeProfiles );

        project.setOriginalModel( superModel );

        try
        {
            project.setFile( new File( ".", "pom.xml" ) );

            List remoteRepositories = buildArtifactRepositories( superModel );

            project = processProjectLogic( "<Super-POM>", project, remoteRepositories, null );

            return project;
        }
        catch ( ModelInterpolationException e )
        {
            throw new ProjectBuildingException( "Error building super-project", e );
        }
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    private Model getSuperModel()
        throws ProjectBuildingException
    {
        URL url = DefaultMavenProjectBuilder.class.getResource( "pom-" + MAVEN_MODEL_VERSION + ".xml" );

        return readModel( url );
    }

    public void contextualize( Context context )
        throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }
}
