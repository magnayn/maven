package org.apache.maven.artifact.transform;

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
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.LegacyArtifactMetadata;
import org.apache.maven.artifact.metadata.ReleaseArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;

import java.util.List;

/**
 * Change the version <code>RELEASE</code> to the appropriate release version from the remote repository.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class ReleaseArtifactTransformation
    extends AbstractVersionTransformation
{
    public static final String RELEASE_VERSION = "RELEASE";

    public void transformForResolve( Artifact artifact, List remoteRepositories, ArtifactRepository localRepository )
        throws ArtifactMetadataRetrievalException
    {
        if ( RELEASE_VERSION.equals( artifact.getVersion() ) )
        {
            String version = resolveVersion( artifact, localRepository, remoteRepositories );

            if ( version != null && !version.equals( artifact.getVersion() ) )
            {
                artifact.setBaseVersion( version );
                artifact.updateVersion( version, localRepository );
            }
        }
    }

    public void transformForInstall( Artifact artifact, ArtifactRepository localRepository )
        throws ArtifactMetadataRetrievalException
    {
        // metadata is added at install time
    }

    public void transformForDeployment( Artifact artifact, ArtifactRepository remoteRepository,
                                        ArtifactRepository localRepository )
        throws ArtifactMetadataRetrievalException
    {
        // metadata is added at deploy time
    }

    protected LegacyArtifactMetadata createLegacyMetadata( Artifact artifact )
    {
        return new ReleaseArtifactMetadata( artifact );
    }

    protected String constructVersion( ArtifactMetadata metadata )
    {
        return metadata.getReleaseVersion();
    }
}
