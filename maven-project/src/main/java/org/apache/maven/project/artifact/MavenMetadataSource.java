package org.apache.maven.project.artifact;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataResolutionException;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Relocation;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.validation.ModelValidationResult;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Jason van Zyl
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class MavenMetadataSource
    extends AbstractLogEnabled
    implements ArtifactMetadataSource, Contextualizable
{
    public static final String ROLE_HINT = "default";

    private MavenProjectBuilder mavenProjectBuilder;

    private ArtifactFactory artifactFactory;

    private RepositoryMetadataManager repositoryMetadataManager;

    // lazily instantiated and cached.
    private MavenProject superProject;

    private PlexusContainer container;

    /** Unfortunately we have projects that are still sending us JARs without the accompanying POMs. */
    private boolean strictlyEnforceThePresenceOfAValidMavenPOM = false;

    /**
     * Retrieve the metadata for the project from the repository.
     * Uses the ProjectBuilder, to enable post-processing and inheritance calculation before retrieving the
     * associated artifacts.
     */
    public ResolutionGroup retrieve( Artifact artifact, ArtifactRepository localRepository, List remoteRepositories )
        throws ArtifactMetadataRetrievalException
    {
        if ( remoteRepositories == null )
        {
            remoteRepositories = Collections.EMPTY_LIST;
        }

        try
        {
            loadProjectBuilder();
        }
        catch ( ComponentLookupException e )
        {
            throw new ArtifactMetadataRetrievalException( "Cannot lookup MavenProjectBuilder component instance: " + e.getMessage(), e );
        }

        MavenProject project = null;

        Artifact pomArtifact;

        boolean done = false;
        do
        {
            // TODO: can we just modify the original?
            pomArtifact = artifactFactory.createProjectArtifact( artifact.getGroupId(), artifact.getArtifactId(),
                                                                 artifact.getVersion(), artifact.getScope() );

            if ( Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
            {
                done = true;
            }
            else
            {
                try
                {
                    project = mavenProjectBuilder.buildFromRepository( pomArtifact, remoteRepositories, localRepository );
                }
                catch ( InvalidProjectModelException e )
                {
                    if ( strictlyEnforceThePresenceOfAValidMavenPOM )
                    {
                        throw new ArtifactMetadataRetrievalException( "Invalid POM file for artifact: '" +
                            artifact.getDependencyConflictId() + "' Reason: " + e.getMessage(), e, artifact );
                    }

                    getLogger().warn( "POM for \'" + pomArtifact +
                        "\' is invalid. It will be ignored for artifact resolution. Reason: " + e.getMessage() );

                    if ( getLogger().isDebugEnabled() )
                    {
                        getLogger().debug( "Reason: " + e.getMessage() );

                        ModelValidationResult validationResult = e.getValidationResult();

                        if ( validationResult != null )
                        {
                            getLogger().debug( "\nValidation Errors:" );
                            for ( Iterator i = validationResult.getMessages().iterator(); i.hasNext(); )
                            {
                                getLogger().debug( i.next().toString() );
                            }
                            getLogger().debug( "\n" );
                        }
                        else
                        {
                            getLogger().debug( "", e );
                        }
                    }

                    project = null;
                }
                catch ( ProjectBuildingException e )
                {
                    if ( strictlyEnforceThePresenceOfAValidMavenPOM )
                    {
                        throw new ArtifactMetadataRetrievalException( "Unable to read the metadata file for artifact '" +
                            artifact.getDependencyConflictId() + "': " + e.getMessage(), e, artifact );
                    }
                }

                if ( project != null )
                {
                    Relocation relocation = null;

                    DistributionManagement distMgmt = project.getDistributionManagement();
                    if ( distMgmt != null )
                    {
                        relocation = distMgmt.getRelocation();

                        artifact.setDownloadUrl( distMgmt.getDownloadUrl() );
                        pomArtifact.setDownloadUrl( distMgmt.getDownloadUrl() );
                    }

                    if ( relocation != null )
                    {
                        if ( relocation.getGroupId() != null )
                        {
                            artifact.setGroupId( relocation.getGroupId() );
                        }
                        if ( relocation.getArtifactId() != null )
                        {
                            artifact.setArtifactId( relocation.getArtifactId() );
                        }
                        if ( relocation.getVersion() != null )
                        {
                            artifact.setVersionRange( VersionRange.createFromVersion( relocation.getVersion() ) );
                        }

                        if ( ( artifact.getDependencyFilter() != null ) &&
                            !artifact.getDependencyFilter().include( artifact ) )
                        {
                            return null;
                        }

                        //MNG-2861: the artifact data has changed. If the available versions where previously retrieved,
                        //we need to update it. TODO: shouldn't the versions be merged across relocations?
                        List available = artifact.getAvailableVersions();
                        if ( available != null && !available.isEmpty() )
                        {
                            artifact.setAvailableVersions( retrieveAvailableVersions( artifact, localRepository,
                                                                                           remoteRepositories ) );

                        }

                        String message = "\n  This artifact has been relocated to " + artifact.getGroupId() + ":" +
                            artifact.getArtifactId() + ":" + artifact.getVersion() + ".\n";

                        if ( relocation.getMessage() != null )
                        {
                            message += "  " + relocation.getMessage() + "\n";
                        }

                        if ( ( artifact.getDependencyTrail() != null ) && ( artifact.getDependencyTrail().size() == 1 ) )
                        {
                            getLogger().warn( "While downloading " + artifact.getGroupId() + ":" +
                                artifact.getArtifactId() + ":" + artifact.getVersion() + message + "\n" );
                        }
                        else
                        {
                            getLogger().debug( "While downloading " + artifact.getGroupId() + ":" +
                                artifact.getArtifactId() + ":" + artifact.getVersion() + message + "\n" );
                        }
                    }
                    else
                    {
                        done = true;
                    }
                }
                else
                {
                    done = true;
                }
            }
        }
        while ( !done );

        // last ditch effort to try to get this set...
        if ( artifact.getDownloadUrl() == null )
        {
            // TODO: this could come straight from the project, negating the need to set it in the project itself?
            artifact.setDownloadUrl( pomArtifact.getDownloadUrl() );
        }

        ResolutionGroup result;

        if ( project == null )
        {
            // if the project is null, we encountered an invalid model (read: m1 POM)
            // we'll just return an empty resolution group.
            // or used the inherited scope (should that be passed to the buildFromRepository method above?)
            result = new ResolutionGroup( pomArtifact, Collections.EMPTY_SET, Collections.EMPTY_LIST );
        }
        else
        {
            Set artifacts = Collections.EMPTY_SET;
            if ( !artifact.getArtifactHandler().isIncludesDependencies() )
            {
                // TODO: we could possibly use p.getDependencyArtifacts instead of this call, but they haven't been filtered
                // or used the inherited scope (should that be passed to the buildFromRepository method above?)
                try
                {
                    artifacts = project.createArtifacts( artifactFactory, artifact.getScope(),
                                                         artifact.getDependencyFilter() );
                }
                catch ( InvalidDependencyVersionException e )
                {
                    throw new ArtifactMetadataRetrievalException( "Error in metadata for artifact '" +
                        artifact.getDependencyConflictId() + "': " + e.getMessage(), e );
                }
            }

            List repositories = aggregateRepositoryLists( remoteRepositories, project.getRemoteArtifactRepositories() );

            result = new ResolutionGroup( pomArtifact, artifacts, repositories );
        }

        return result;
    }

    private void loadProjectBuilder()
        throws ComponentLookupException
    {
        if ( mavenProjectBuilder == null )
        {
            mavenProjectBuilder = (MavenProjectBuilder) container.lookup( MavenProjectBuilder.class );
        }
    }

    private List aggregateRepositoryLists( List remoteRepositories, List remoteArtifactRepositories )
        throws ArtifactMetadataRetrievalException
    {
        if ( superProject == null )
        {
            try
            {
                superProject = mavenProjectBuilder.buildStandaloneSuperProject();
            }
            catch ( ProjectBuildingException e )
            {
                throw new ArtifactMetadataRetrievalException(
                    "Unable to parse the Maven built-in model: " + e.getMessage(), e );
            }
        }

        List repositories = new ArrayList();

        repositories.addAll( remoteRepositories );

        // ensure that these are defined
        for ( Iterator it = superProject.getRemoteArtifactRepositories().iterator(); it.hasNext(); )
        {
            ArtifactRepository superRepo = (ArtifactRepository) it.next();

            for ( Iterator aggregatedIterator = repositories.iterator(); aggregatedIterator.hasNext(); )
            {
                ArtifactRepository repo = (ArtifactRepository) aggregatedIterator.next();

                // if the repository exists in the list and was introduced by another POM's super-pom,
                // remove it...the repository definitions from the super-POM should only be at the end of
                // the list.
                // if the repository has been redefined, leave it.
                if ( repo.getId().equals( superRepo.getId() ) && repo.getUrl().equals( superRepo.getUrl() ) )
                {
                    aggregatedIterator.remove();
                }
            }
        }

        if ( remoteArtifactRepositories != null )
        {
            // this list should contain the super-POM repositories, so we don't have to explicitly add them back.
            for ( Iterator it = remoteArtifactRepositories.iterator(); it.hasNext(); )
            {
                ArtifactRepository repository = (ArtifactRepository) it.next();

                if ( !repositories.contains( repository ) )
                {
                    repositories.add( repository );
                }
            }
        }

        return repositories;
    }

    /**
     * @todo desperately needs refactoring. It's just here because it's implementation is maven-project specific
     * @return {@link Set} &lt; {@link Artifact} >
     */
    public static Set createArtifacts( ArtifactFactory artifactFactory, List dependencies, String inheritedScope,
                                       ArtifactFilter dependencyFilter, MavenProject project )
        throws InvalidDependencyVersionException
    {
        Set projectArtifacts = new LinkedHashSet( dependencies.size() );

        for ( Iterator i = dependencies.iterator(); i.hasNext(); )
        {
            Dependency d = (Dependency) i.next();

            String scope = d.getScope();

            if ( StringUtils.isEmpty( scope ) )
            {
                scope = Artifact.SCOPE_COMPILE;

                d.setScope( scope );
            }

            VersionRange versionRange;
            try
            {
                versionRange = VersionRange.createFromVersionSpec( d.getVersion() );
            }
            catch ( InvalidVersionSpecificationException e )
            {
                throw new InvalidDependencyVersionException( project.getId(), d, project.getFile(), e );
            }
            Artifact artifact = artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(),
                                                                          versionRange, d.getType(), d.getClassifier(),
                                                                          scope, inheritedScope, d.isOptional() );

            if ( Artifact.SCOPE_SYSTEM.equals( scope ) )
            {
                artifact.setFile( new File( d.getSystemPath() ) );
            }

            ArtifactFilter artifactFilter = dependencyFilter;

            if ( ( artifact != null ) && ( ( artifactFilter == null ) || artifactFilter.include( artifact ) ) )
            {
                if ( ( d.getExclusions() != null ) && !d.getExclusions().isEmpty() )
                {
                    List exclusions = new ArrayList();
                    for ( Iterator j = d.getExclusions().iterator(); j.hasNext(); )
                    {
                        Exclusion e = (Exclusion) j.next();
                        exclusions.add( e.getGroupId() + ":" + e.getArtifactId() );
                    }

                    ArtifactFilter newFilter = new ExcludesArtifactFilter( exclusions );

                    if ( artifactFilter != null )
                    {
                        AndArtifactFilter filter = new AndArtifactFilter();
                        filter.add( artifactFilter );
                        filter.add( newFilter );
                        artifactFilter = filter;
                    }
                    else
                    {
                        artifactFilter = newFilter;
                    }
                }

                artifact.setDependencyFilter( artifactFilter );

                if ( project != null )
                {
                    artifact = project.replaceWithActiveArtifact( artifact );
                }

                projectArtifacts.add( artifact );
            }
        }

        return projectArtifacts;
    }

    public List retrieveAvailableVersions( Artifact artifact, ArtifactRepository localRepository,
                                           List remoteRepositories )
        throws ArtifactMetadataRetrievalException
    {
        RepositoryMetadata metadata = new ArtifactRepositoryMetadata( artifact );
        try
        {
            repositoryMetadataManager.resolve( metadata, remoteRepositories, localRepository );
        }
        catch ( RepositoryMetadataResolutionException e )
        {
            throw new ArtifactMetadataRetrievalException( e.getMessage(), e );
        }

        List versions;
        Metadata repoMetadata = metadata.getMetadata();
        if ( ( repoMetadata != null ) && ( repoMetadata.getVersioning() != null ) )
        {
            List metadataVersions = repoMetadata.getVersioning().getVersions();
            versions = new ArrayList( metadataVersions.size() );
            for ( Iterator i = metadataVersions.iterator(); i.hasNext(); )
            {
                String version = (String) i.next();
                versions.add( new DefaultArtifactVersion( version ) );
            }
        }
        else
        {
            versions = Collections.EMPTY_LIST;
        }

        return versions;
    }

    public void contextualize( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }
}
