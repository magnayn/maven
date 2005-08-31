package org.apache.maven.artifact.resolver;

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
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.transform.ArtifactTransformationManager;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultArtifactResolver
    extends AbstractLogEnabled
    implements ArtifactResolver
{
    // ----------------------------------------------------------------------
    // Components
    // ----------------------------------------------------------------------

    private WagonManager wagonManager;

    private ArtifactTransformationManager transformationManager;

    protected ArtifactFactory artifactFactory;

    private ArtifactCollector artifactCollector;

    // ----------------------------------------------------------------------
    // Implementation
    // ----------------------------------------------------------------------

    public void resolve( Artifact artifact, List remoteRepositories, ArtifactRepository localRepository )
        throws ArtifactResolutionException
    {
        resolve( artifact, remoteRepositories, localRepository, false );
    }

    public void resolveAlways( Artifact artifact, List remoteRepositories, ArtifactRepository localRepository )
        throws ArtifactResolutionException
    {
        resolve( artifact, remoteRepositories, localRepository, true );
    }

    private void resolve( Artifact artifact, List remoteRepositories, ArtifactRepository localRepository,
                          boolean force )
        throws ArtifactResolutionException
    {
        if ( artifact != null )
        {
            if ( Artifact.SCOPE_SYSTEM.equals( artifact.getScope() ) )
            {
                File systemFile = artifact.getFile();

                if ( !systemFile.exists() )
                {
                    throw new ArtifactResolutionException( "System artifact: " + artifact.getId()
                        + " not found in path: " + systemFile, artifact );
                }
                else
                {
                    artifact.setResolved( true );
                }
            }
            // skip artifacts with a file - they are already resolved
            else if ( artifact.getFile() == null )
            {
                // ----------------------------------------------------------------------
                // Check for the existence of the artifact in the specified local
                // ArtifactRepository. If it is present then simply return as the
                // request for resolution has been satisfied.
                // ----------------------------------------------------------------------

                String localPath = localRepository.pathOf( artifact );

                artifact.setFile( new File( localRepository.getBasedir(), localPath ) );

                try
                {
                    transformationManager.transformForResolve( artifact, remoteRepositories, localRepository );
                }
                catch ( ArtifactMetadataRetrievalException e )
                {
                    throw new ArtifactResolutionException( e.getMessage(), artifact, remoteRepositories, e );
                }

                File destination = artifact.getFile();
                if ( !destination.exists() || force )
                {
                    try
                    {
                        if ( artifact.getRepository() != null )
                        {
                            // the transformations discovered the artifact - so use it exclusively
                            wagonManager.getArtifact( artifact, artifact.getRepository() );
                        }
                        else
                        {
                            wagonManager.getArtifact( artifact, remoteRepositories );
                        }

                        if ( !artifact.isResolved() )
                        {
                            throw new ArtifactResolutionException(
                                                                   "Failed to resolve artifact, possibly due to a repository list that is not appropriately equipped for this artifact's metadata.",
                                                                   artifact, remoteRepositories );
                        }

                        // must be after the artifact is downloaded
                        for ( Iterator i = artifact.getMetadataList().iterator(); i.hasNext(); )
                        {
                            ArtifactMetadata metadata = (ArtifactMetadata) i.next();
                            metadata.storeInLocalRepository( localRepository );
                        }
                    }
                    catch ( ResourceDoesNotExistException e )
                    {
                        throw new ArtifactResolutionException( e.getMessage(), artifact, remoteRepositories, e );
                    }
                    catch ( TransferFailedException e )
                    {
                        throw new ArtifactResolutionException( e.getMessage(), artifact, remoteRepositories, e );
                    }
                    catch ( ArtifactMetadataRetrievalException e )
                    {
                        throw new ArtifactResolutionException( e.getMessage(), artifact, remoteRepositories, e );
                    }
                }
                else if ( destination.exists() )
                {
                    // locally resolved...no need to hit the remote repo.
                    artifact.setResolved( true );
                }
            }
        }
    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         ArtifactRepository localRepository, List remoteRepositories,
                                                         ArtifactMetadataSource source, ArtifactFilter filter )
        throws ArtifactResolutionException
    {
        return resolveTransitively( artifacts, originatingArtifact, Collections.EMPTY_MAP, localRepository,
                                    remoteRepositories, source, filter );

    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         Map managedVersions, ArtifactRepository localRepository,
                                                         List remoteRepositories, ArtifactMetadataSource source )
        throws ArtifactResolutionException
    {
        return resolveTransitively( artifacts, originatingArtifact, managedVersions, localRepository,
                                    remoteRepositories, source, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         Map managedVersions, ArtifactRepository localRepository,
                                                         List remoteRepositories, ArtifactMetadataSource source,
                                                         ArtifactFilter filter )
        throws ArtifactResolutionException
    {
        // TODO: this is simplistic
        List listeners = new ArrayList();
        if ( getLogger().isDebugEnabled() )
        {
            listeners.add( new DebugResolutionListener( getLogger() ) );
        }

        listeners.add( new WarningResolutionListener( getLogger() ) );

        return resolveTransitively( artifacts, originatingArtifact, managedVersions, localRepository,
                                    remoteRepositories, source, filter, listeners );

    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         Map managedVersions, ArtifactRepository localRepository,
                                                         List remoteRepositories, ArtifactMetadataSource source,
                                                         ArtifactFilter filter, List listeners )
        throws ArtifactResolutionException
    {
        ArtifactResolutionResult artifactResolutionResult;
        artifactResolutionResult = artifactCollector.collect( artifacts, originatingArtifact, managedVersions,
                                                              localRepository, remoteRepositories, source, filter,
                                                              artifactFactory, listeners );

        for ( Iterator i = artifactResolutionResult.getArtifactResolutionNodes().iterator(); i.hasNext(); )
        {
            ResolutionNode node = (ResolutionNode) i.next();
            resolve( node.getArtifact(), node.getRemoteRepositories(), localRepository );
        }

        return artifactResolutionResult;
    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         List remoteRepositories, ArtifactRepository localRepository,
                                                         ArtifactMetadataSource source )
        throws ArtifactResolutionException
    {
        return resolveTransitively( artifacts, originatingArtifact, localRepository, remoteRepositories, source, null );
    }

    public ArtifactResolutionResult resolveTransitively( Set artifacts, Artifact originatingArtifact,
                                                         List remoteRepositories, ArtifactRepository localRepository,
                                                         ArtifactMetadataSource source, List listeners )
        throws ArtifactResolutionException
    {
        return resolveTransitively( artifacts, originatingArtifact, Collections.EMPTY_MAP, localRepository,
                                    remoteRepositories, source, null, listeners );
    }

}