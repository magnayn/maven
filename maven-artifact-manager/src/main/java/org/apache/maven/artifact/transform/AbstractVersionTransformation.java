package org.apache.maven.artifact.transform;

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
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataResolutionException;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.util.List;

/**
 * Describes a version transformation during artifact resolution.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 * @todo try and refactor to remove abstract methods - not particular happy about current design
 */
public abstract class AbstractVersionTransformation
    extends AbstractLogEnabled
    implements ArtifactTransformation
{
    protected RepositoryMetadataManager repositoryMetadataManager;

    protected WagonManager wagonManager;

    protected String resolveVersion( Artifact artifact, ArtifactRepository localRepository, List remoteRepositories )
        throws RepositoryMetadataResolutionException
    {
        RepositoryMetadata metadata;
        // Don't use snapshot metadata for LATEST (which isSnapshot returns true for)
        if ( !artifact.isSnapshot() || Artifact.LATEST_VERSION.equals( artifact.getBaseVersion() ) )
        {
            metadata = new ArtifactRepositoryMetadata( artifact );
        }
        else
        {
            metadata = new SnapshotArtifactRepositoryMetadata( artifact );
        }

        repositoryMetadataManager.resolve( metadata, remoteRepositories, localRepository );

        artifact.addMetadata( metadata );

        Metadata repoMetadata = metadata.getMetadata();
        String version = null;
        if ( repoMetadata != null && repoMetadata.getVersioning() != null )
        {
            version = constructVersion( repoMetadata.getVersioning(), artifact.getBaseVersion() );
        }

        if ( version == null )
        {
            // use the local copy, or if it doesn't exist - go to the remote repo for it
            version = artifact.getBaseVersion();
        }

        // TODO: also do this logging for other metadata?
        // TODO: figure out way to avoid duplicated message
        if ( getLogger().isDebugEnabled() )
        {
            if ( !version.equals( artifact.getBaseVersion() ) )
            {
                String message = artifact.getArtifactId() + ": resolved to version " + version;
                if ( artifact.getRepository() != null )
                {
                    message += " from repository " + artifact.getRepository().getId();
                }
                else
                {
                    message += " from local repository";
                }
                getLogger().debug( message );
            }
            else
            {
                // Locally installed file is newer, don't use the resolved version
                getLogger().debug( artifact.getArtifactId() + ": using locally installed snapshot" );
            }
        }
        return version;
    }

    protected abstract String constructVersion( Versioning versioning, String baseVersion );
}
