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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.profiles.manager.ProfileManager;
import org.apache.maven.wagon.events.TransferListener;

import java.io.File;
import java.util.List;

public interface MavenProjectBuilder
{
    String ROLE = MavenProjectBuilder.class.getName();

    String STANDALONE_SUPERPOM_GROUPID = "org.apache.maven";

    String STANDALONE_SUPERPOM_ARTIFACTID = "super-pom";

    String STANDALONE_SUPERPOM_VERSION = "2.0";

    boolean STRICT_MODEL_PARSING = true;

    MavenProject build( File project, ArtifactRepository localRepository, ProfileManager globalProfileManager )
        throws ProjectBuildingException;

    MavenProject build( File project, ArtifactRepository localRepository, ProfileManager globalProfileManager,
                        boolean checkDistributionManagementStatus )
        throws ProjectBuildingException;

    // ----------------------------------------------------------------------
    // These methods are used by the MavenEmbedder
    // ----------------------------------------------------------------------

    MavenProjectBuildingResult buildWithDependencies( File project, ArtifactRepository localRepository,
                                        ProfileManager globalProfileManager, TransferListener transferListener )
        throws ProjectBuildingException;

    MavenProjectBuildingResult buildWithDependencies( File project, ArtifactRepository localRepository,
                                        ProfileManager globalProfileManager )
        throws ProjectBuildingException;

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

    MavenProject buildStandaloneSuperProject()
        throws ProjectBuildingException;
}
