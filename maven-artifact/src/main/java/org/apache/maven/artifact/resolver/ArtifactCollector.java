package org.apache.maven.artifact.resolver;

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
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Artifact collector - takes a set of original artifacts and resolves all of the best versions to use
 * along with their metadata. No artifacts are downloaded.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public interface ArtifactCollector
{
    ArtifactResolutionResult collect( Set artifacts, Artifact originatingArtifact, ArtifactRepository localRepository,
                                      List remoteRepositories, ArtifactMetadataSource source, ArtifactFilter filter,
                                      List listeners )
        throws ArtifactResolutionException;

    ArtifactResolutionResult collect( Set artifacts, Artifact originatingArtifact, Map managedVersions,
                                      ArtifactRepository localRepository, List remoteRepositories,
                                      ArtifactMetadataSource source, ArtifactFilter filter, List listeners )
        throws ArtifactResolutionException;
}
