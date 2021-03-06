package org.apache.maven.artifact.resolver;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.resolver.conflict.ConflictResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.transform.ArtifactTransformationManager;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.FileUtils;

/**
 * @author Jason van Zyl
 */
@Component(role = ArtifactResolver.class)
public class DefaultArtifactResolver
    extends AbstractLogEnabled
    implements ArtifactResolver
{

    private boolean online = true;

    // ----------------------------------------------------------------------
    // Components
    // ----------------------------------------------------------------------

    @Requirement
    private WagonManager wagonManager;

    @Requirement
    private ArtifactTransformationManager transformationManager;

    @Requirement
    protected ArtifactFactory artifactFactory;

    @Requirement
    private ArtifactCollector artifactCollector;

    @Requirement
    private ResolutionErrorHandler resolutionErrorHandler;

    // ----------------------------------------------------------------------
    // Implementation
    // ----------------------------------------------------------------------

    public void setOnline( boolean online )
    {
        this.online = online;
    }

    public boolean isOnline()
    {
        return online;
    }

    public void resolve( Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        resolve( artifact, remoteRepositories, localRepository, false );
    }

    public void resolveAlways( Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        resolve( artifact, remoteRepositories, localRepository, true );
    }

    private void resolve( Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository, boolean force )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        if ( artifact == null )
        {
            return;
        }

        File destination;
        if ( Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
        {
            File systemFile = artifact.getFile();

            if ( systemFile == null )
            {
                throw new ArtifactNotFoundException( "System artifact: " + artifact + " has no file attached", artifact );
            }

            if ( !systemFile.exists() )
            {
                throw new ArtifactNotFoundException( "System artifact: " + artifact + " not found in path: " + systemFile, artifact );
            }

            if ( !systemFile.isFile() )
            {
                throw new ArtifactNotFoundException( "System artifact: " + artifact + " is not a file: " + systemFile, artifact );
            }

            artifact.setResolved( true );
        }
        else if ( !artifact.isResolved() )
        {
            // ----------------------------------------------------------------------
            // Check for the existence of the artifact in the specified local
            // ArtifactRepository. If it is present then simply return as the
            // request for resolution has been satisfied.
            // ----------------------------------------------------------------------

            String localPath = localRepository.pathOf( artifact );

            artifact.setFile( new File( localRepository.getBasedir(), localPath ) );

            transformationManager.transformForResolve( artifact, remoteRepositories, localRepository );

            boolean localCopy = isLocalCopy( artifact );

            destination = artifact.getFile();

            boolean resolved = false;

            // There are three conditions in which we'll go after the artifact here:
            // 1. the force flag is set.
            // 2. the artifact's file doesn't exist (this would be true for release or snapshot artifacts)
            // 3. the artifact is a snapshot and is not a locally installed snapshot

            // TODO: Should it matter whether it's a locally installed snapshot??
            if ( force || !destination.exists() || ( artifact.isSnapshot() && !localCopy && isOnline() ) )
            {
                if ( !isOnline() )
                {
                    throw new ArtifactNotFoundException( "The repository system is offline.", artifact );
                }

                try
                {
                    if ( artifact.getRepository() != null )
                    {
                        // the transformations discovered the artifact - so use it exclusively
                        wagonManager.getArtifact( artifact, artifact.getRepository(), force );
                    }
                    else
                    {
                        wagonManager.getArtifact( artifact, remoteRepositories, force );
                    }

                    if ( !artifact.isResolved() && !destination.exists() )
                    {
                        throw new ArtifactResolutionException( "Failed to resolve artifact, possibly due to a repository list that is not appropriately equipped for this artifact's metadata.",
                                                               artifact, remoteRepositories );
                    }
                }
                catch ( ResourceDoesNotExistException e )
                {
                    throw new ArtifactNotFoundException( e.getMessage(), artifact, remoteRepositories, e );
                }
                catch ( TransferFailedException e )
                {
                    throw new ArtifactResolutionException( e.getMessage(), artifact, remoteRepositories, e );
                }

                resolved = true;
            }

            if ( destination.exists() )
            {
                // locally resolved...no need to hit the remote repo.
                artifact.setResolved( true );
            }

            if ( artifact.isSnapshot() && !artifact.getBaseVersion().equals( artifact.getVersion() ) )
            {
                String version = artifact.getVersion();

                artifact.selectVersion( artifact.getBaseVersion() );

                File copy = new File( localRepository.getBasedir(), localRepository.pathOf( artifact ) );

                if ( resolved || !copy.exists() )
                {
                    // recopy file if it was reresolved, or doesn't exist.
                    try
                    {
                        FileUtils.copyFile( destination, copy );

                        copy.setLastModified( destination.lastModified() );
                    }
                    catch ( IOException e )
                    {
                        throw new ArtifactResolutionException( "Unable to copy resolved artifact for local use: " + e.getMessage(), artifact, remoteRepositories, e );
                    }
                }

                artifact.setFile( copy );

                artifact.selectVersion( version );
            }
        }
    }

    private boolean isLocalCopy( Artifact artifact )
    {
        boolean localCopy = false;

        for ( ArtifactMetadata m : artifact.getMetadataList() )
        {
            if ( m instanceof SnapshotArtifactRepositoryMetadata )
            {
                SnapshotArtifactRepositoryMetadata snapshotMetadata = (SnapshotArtifactRepositoryMetadata) m;

                Metadata metadata = snapshotMetadata.getMetadata();

                if ( metadata != null )
                {
                    Versioning versioning = metadata.getVersioning();

                    if ( versioning != null )
                    {
                        Snapshot snapshot = versioning.getSnapshot();

                        if ( snapshot != null )
                        {
                            // TODO is it possible to have more than one SnapshotArtifactRepositoryMetadata
                            localCopy = snapshot.isLocalCopy();
                        }
                    }
                }
            }
        }

        return localCopy;
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, ArtifactRepository localRepository, List<ArtifactRepository> remoteRepositories,
                                                         ArtifactMetadataSource source, ArtifactFilter filter )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact, Collections.EMPTY_MAP, localRepository, remoteRepositories, source, filter );

    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, Map managedVersions, ArtifactRepository localRepository,
                                                         List<ArtifactRepository> remoteRepositories, ArtifactMetadataSource source )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact, managedVersions, localRepository, remoteRepositories, source, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, Map managedVersions, ArtifactRepository localRepository,
                                                         List<ArtifactRepository> remoteRepositories, ArtifactMetadataSource source, ArtifactFilter filter )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact, managedVersions, localRepository, remoteRepositories, source, filter, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository,
                                                         ArtifactMetadataSource source )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact, localRepository, remoteRepositories, source, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository,
                                                         ArtifactMetadataSource source, List<ResolutionListener> listeners )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact,

        Collections.EMPTY_MAP, localRepository, remoteRepositories, source, null, listeners );
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, Map managedVersions, ArtifactRepository localRepository,
                                                         List<ArtifactRepository> remoteRepositories, ArtifactMetadataSource source, ArtifactFilter filter, List<ResolutionListener> listeners )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        return resolveTransitively( artifacts, originatingArtifact, managedVersions, localRepository, remoteRepositories, source, filter, listeners, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set<Artifact> artifacts, Artifact originatingArtifact, Map managedVersions, ArtifactRepository localRepository,
                                                         List<ArtifactRepository> remoteRepositories, ArtifactMetadataSource source, ArtifactFilter filter, List<ResolutionListener> listeners,
                                                         List<ConflictResolver> conflictResolvers )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest().setArtifact( originatingArtifact ).setResolveRoot( false ).setArtifactDependencies( artifacts ).setManagedVersionMap( managedVersions )
            .setLocalRepository( localRepository ).setRemoteRepostories( remoteRepositories ).setMetadataSource( source ).setFilter( filter ).setListeners( listeners );

        return resolveWithExceptions( request );
    }

    public ArtifactResolutionResult resolveWithExceptions( ArtifactResolutionRequest request )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        ArtifactResolutionResult result = resolve( request );

        // We have collected all the problems so let's mimic the way the old code worked and just blow up right here.
        // That's right lets just let it rip right here and send a big incomprehensible blob of text at unsuspecting
        // users. Bad dog!

        resolutionErrorHandler.throwErrors( request, result );

        return result;
    }

    // ------------------------------------------------------------------------
    //
    // ------------------------------------------------------------------------

    public ArtifactResolutionResult resolve( ArtifactResolutionRequest request )
    {
        Artifact rootArtifact = request.getArtifact();
        Set<Artifact> artifacts = request.getArtifactDependencies();
        Map managedVersions = request.getManagedVersionMap();
        ArtifactRepository localRepository = request.getLocalRepository();
        List<ArtifactRepository> remoteRepositories = request.getRemoteRepostories();
        ArtifactMetadataSource source = request.getMetadataSource();
        List<ResolutionListener> listeners = request.getListeners();
        ArtifactFilter filter = request.getFilter();
        
        if ( listeners == null )
        {
            listeners = new ArrayList<ResolutionListener>();

            if ( getLogger().isDebugEnabled() )
            {
                listeners.add( new DebugResolutionListener( getLogger() ) );
            }

            listeners.add( new WarningResolutionListener( getLogger() ) );
        }

        ArtifactResolutionResult result = new ArtifactResolutionResult();

        // The root artifact may, or may not be resolved so we need to check before we attempt to resolve.
        // This is often an artifact like a POM that is taken from disk and we already have hold of the
        // file reference. But this may be a Maven Plugin that we need to resolve from a remote repository
        // as well as its dependencies.
        
        if ( request.isResolveRoot() && rootArtifact.getFile() == null )
        {
            try
            {
                resolve( rootArtifact, remoteRepositories, localRepository );
                result.addArtifact( rootArtifact );
            }
            catch ( ArtifactResolutionException e )
            {
                result.addErrorArtifactException( e );
                return result;
            }
            catch ( ArtifactNotFoundException e )
            {
                result.addMissingArtifact( request.getArtifact() );
                return result;
            }
        }

        if ( artifacts == null || artifacts.size() == 0 )
        {
            return result;
        } 
        
        // After the collection we will have the artifact object in the result but they will not be resolved yet.
        result = artifactCollector.collect( artifacts, rootArtifact, managedVersions, localRepository, remoteRepositories, source, filter, listeners );

        // We have metadata retrieval problems, or there are cycles that have been detected
        // so we give this back to the calling code and let them deal with this information
        // appropriately.

        if ( result.hasMetadataResolutionExceptions() || result.hasVersionRangeViolations() || result.hasCircularDependencyExceptions() )
        {
            return result;
        }

        if ( result.getArtifacts() != null )
        {
            for ( Artifact artifact : result.getArtifacts() )
            {
                try
                {
                    resolve( artifact, remoteRepositories, localRepository );
                }
                catch ( ArtifactNotFoundException anfe )
                {
                    // These are cases where the artifact just isn't present in any of the remote repositories
                    // because it wasn't deployed, or it was deployed in the wrong place.

                    result.addMissingArtifact( artifact );
                }
                catch ( ArtifactResolutionException e )
                {
                    // This is really a wagon TransferFailedException so something went wrong after we successfully
                    // retrieved the metadata.

                    result.addErrorArtifactException( e );
                }
            }
        }

        return result;
    }
}
