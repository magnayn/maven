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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.wagon.events.TransferListener;

import java.io.File;
import java.util.List;

public interface MavenProjectBuilder
{
    String ROLE = MavenProjectBuilder.class.getName();

    String STANDALONE_SUPERPOM_GROUPID = "org.apache.maven";

    String STANDALONE_SUPERPOM_ARTIFACTID = "super-pom";

    String STANDALONE_SUPERPOM_VERSION = "2.0";

    MavenProject build( File project, ArtifactRepository localRepository, ProfileManager globalProfileManager )
        throws ProjectBuildingException;

    // ----------------------------------------------------------------------
    // These methods are used by the MavenEmbedder
    // ----------------------------------------------------------------------

    MavenProject buildWithDependencies( File project, ArtifactRepository localRepository,
                                        ProfileManager globalProfileManager, TransferListener transferListener )
        throws ProjectBuildingException, ArtifactResolutionException, ArtifactNotFoundException;

    MavenProject buildWithDependencies( File project, ArtifactRepository localRepository,
                                        ProfileManager globalProfileManager )
        throws ProjectBuildingException, ArtifactResolutionException, ArtifactNotFoundException;

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    /**
     * Build the artifact from the local repository, resolving it if necessary.
     *
     * @param artifact the artifact description
     * @param localRepository the local repository
     * @param remoteArtifactRepositories the remote repository list
     * @return the built project
     * @throws ProjectBuildingException
     */
    MavenProject buildFromRepository( Artifact artifact, List remoteArtifactRepositories,
                                      ArtifactRepository localRepository )
        throws ProjectBuildingException;

    /**
     * Build the artifact from the local repository, resolving it if necessary.
     *
     * @param artifact the artifact description
     * @param localRepository the local repository
     * @param remoteArtifactRepositories the remote repository list
     * @param allowStubModel return a stub if the POM is not found
     * @return the built project
     * @throws ProjectBuildingException
     */
    MavenProject buildFromRepository( Artifact artifact, List remoteArtifactRepositories,
                                      ArtifactRepository localRepository, boolean allowStubModel )
        throws ProjectBuildingException;

    MavenProject buildStandaloneSuperProject( ArtifactRepository localRepository )
        throws ProjectBuildingException;
}
