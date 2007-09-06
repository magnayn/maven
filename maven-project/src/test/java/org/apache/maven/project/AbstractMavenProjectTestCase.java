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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.profiles.manager.DefaultProfileManager;
import org.codehaus.plexus.PlexusTestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl </a>
 * @version $Id$
 */
public abstract class AbstractMavenProjectTestCase
    extends PlexusTestCase
{
    protected MavenProjectBuilder projectBuilder;

    protected void setUp()
        throws Exception
    {
        super.setUp();

        if ( getContainer().hasComponent( MavenProjectBuilder.ROLE, "test" ) )
        {
            projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE, "test" );
        }
        else
        {
            // default over to the main project builder...
            projectBuilder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
        }
    }

    // ----------------------------------------------------------------------
    // Local repository
    // ----------------------------------------------------------------------

    protected File getLocalRepositoryPath()
        throws FileNotFoundException, URISyntaxException
    {
        File markerFile = getFileForClasspathResource( "local-repo/marker.txt" );

        return markerFile.getAbsoluteFile().getParentFile();
    }

    protected File getFileForClasspathResource( String resource )
        throws FileNotFoundException, URISyntaxException
    {
        ClassLoader cloader = Thread.currentThread().getContextClassLoader();

        URL resourceUrl = cloader.getResource( resource );

        if ( resourceUrl == null )
        {
            throw new FileNotFoundException( "Unable to find: " + resource );
        }

        return new File( new URI( resourceUrl.toString() ) );
    }

    protected ArtifactRepository getLocalRepository()
        throws Exception
    {
        ArtifactRepositoryLayout repoLayout = (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE,
                                                                                 "legacy" );

        ArtifactRepository r = new DefaultArtifactRepository( "local",
                                                              "file://" + getLocalRepositoryPath().getAbsolutePath(),
                                                              repoLayout );

        return r;
    }

    // ----------------------------------------------------------------------
    // Project building
    // ----------------------------------------------------------------------

    protected MavenProject getProjectWithDependencies( File pom )
        throws Exception
    {
        return projectBuilder.buildWithDependencies( pom, getLocalRepository(), null ).getProject();
    }

    protected MavenProject getProject( File pom )
        throws Exception
    {
        return projectBuilder.build( pom, getLocalRepository(), new DefaultProfileManager( getContainer() ) );
    }

}
