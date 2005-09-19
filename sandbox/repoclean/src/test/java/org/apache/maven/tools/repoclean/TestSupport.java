package org.apache.maven.tools.repoclean;

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

import java.io.File;
import java.net.URL;

public final class TestSupport
{

    private static final String REPO_SUBDIR = "repo/";

    private static final String REPO_MARKER = "repo-marker.txt";

    private static final int PACKAGE_TRIM = TestSupport.class.getPackage().getName().length() + 1;

    private TestSupport()
    {
    }

    public static String getMyRepositoryPath( Object testInstance )
    {
        Class testClass = testInstance.getClass();

        String myRepo = testClass.getName().substring( PACKAGE_TRIM );

        return getRepositoryPath( myRepo );
    }

    public static File getMyResource( Object testInstance, String relativePath )
    {
        Class testClass = testInstance.getClass();

        String myPath = testClass.getName().substring( PACKAGE_TRIM );

        String resource = myPath.replace( '.', '/' );

        if ( !relativePath.startsWith( "/" ) )
        {
            resource += "/";
        }

        resource += relativePath;

        return getResource( resource );
    }

    public static String getRepositoryPath( String relativePath )
    {
        String base = relativePath.replace( '.', '/' );

        if ( !base.endsWith( "/" ) )
        {
            base += "/";
        }

        return getResource( base + REPO_SUBDIR + REPO_MARKER ).getParentFile().getAbsolutePath();
    }

    public static File getResource( String relativePath )
    {
        ClassLoader cloader = TestSupport.class.getClassLoader();

        URL resource = cloader.getResource( relativePath );

        return new File( resource.getPath() ).getAbsoluteFile();
    }

}
