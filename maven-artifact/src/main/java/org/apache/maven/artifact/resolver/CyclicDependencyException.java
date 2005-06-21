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

import java.util.List;

/**
 * Indiciates a cycle in the dependency graph.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
                      public class CyclicDependencyException
    extends ArtifactResolutionException
{
    public CyclicDependencyException( String message, String groupId, String artifactId, String version, String type,
                                      List remoteRepositories, String downloadUrl, Throwable t )
    {
        super( message, groupId, artifactId, version, type, remoteRepositories, downloadUrl, t );
    }

    public CyclicDependencyException( String message, Artifact artifact, List remoteRepositories, Throwable t )
    {
        super( message, artifact, remoteRepositories, t );
    }

    public CyclicDependencyException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
