package org.apache.maven.extension;

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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainerException;

import java.util.List;

/**
 * Used to locate extensions.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public interface ExtensionManager
{
    void addExtension( Extension extension, MavenProject project, ArtifactRepository localRepository )
        throws ArtifactResolutionException, PlexusContainerException, ArtifactNotFoundException;
    
    void registerWagons();

    void addExtension( Extension extension, Model originatingModel, List remoteRepositories,
                       ArtifactRepository localRepository )
        throws ArtifactResolutionException, PlexusContainerException, ArtifactNotFoundException;
}
