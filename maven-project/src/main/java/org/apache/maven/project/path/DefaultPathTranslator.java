package org.apache.maven.project.path;

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

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DefaultPathTranslator
    implements PathTranslator
{
    private String FILE_SEPARATOR = "/";

    public void alignToBaseDirectory( Model model, File basedir )
    {
        Build build = model.getBuild();

        if ( build != null )
        {
            build.setDirectory( alignToBaseDirectory( build.getDirectory(), basedir ) );

            build.setSourceDirectory( alignToBaseDirectory( build.getSourceDirectory(), basedir ) );

            build.setTestSourceDirectory( alignToBaseDirectory( build.getTestSourceDirectory(), basedir ) );

            for ( Iterator i = build.getResources().iterator(); i.hasNext(); )
            {
                Resource resource = (Resource) i.next();

                resource.setDirectory( alignToBaseDirectory( resource.getDirectory(), basedir ) );
            }

            for ( Iterator i = build.getTestResources().iterator(); i.hasNext(); )
            {
                Resource resource = (Resource) i.next();

                resource.setDirectory( alignToBaseDirectory( resource.getDirectory(), basedir ) );
            }

            if ( build.getFilters() != null )
            {
                List filters = new ArrayList();
                for ( Iterator i = build.getFilters().iterator(); i.hasNext(); )
                {
                    String filter = (String) i.next();

                    filters.add( alignToBaseDirectory( filter, basedir ) );
                }
                build.setFilters( filters );
            }

            build.setOutputDirectory( alignToBaseDirectory( build.getOutputDirectory(), basedir ) );

            build.setTestOutputDirectory( alignToBaseDirectory( build.getTestOutputDirectory(), basedir ) );
        }
    }

    public String alignToBaseDirectory( String path, File basedir )
    {
        String s = stripBasedirToken( path );

        if ( requiresBaseDirectoryAlignment( s ) )
        {
            s = new File( basedir, s ).getAbsolutePath();
        }

        return s;
    }

    private String stripBasedirToken( String s )
    {
        if ( s != null )
        {
            s = s.trim();

            if ( s.startsWith( "${basedir}" ) )
            {
                // Take out ${basedir} and the leading slash
                s = s.substring( 11 );
            }
        }

        return s;
    }

    private boolean requiresBaseDirectoryAlignment( String s )
    {
        if ( s != null )
        {
            File f = new File( s );

            if ( s.startsWith( FILE_SEPARATOR ) || f.isAbsolute() )
            {
                return false;
            }
            else
            {
                return true;
            }
        }

        return false;
    }

    public void unalignFromBaseDirectory( Model model, File basedir )
    {
        Build build = model.getBuild();

        if ( build != null )
        {
            build.setDirectory( unalignFromBaseDirectory( build.getDirectory(), basedir ) );

            build.setSourceDirectory( unalignFromBaseDirectory( build.getSourceDirectory(), basedir ) );

            build.setTestSourceDirectory( unalignFromBaseDirectory( build.getTestSourceDirectory(), basedir ) );

            for ( Iterator i = build.getResources().iterator(); i.hasNext(); )
            {
                Resource resource = (Resource) i.next();

                resource.setDirectory( unalignFromBaseDirectory( resource.getDirectory(), basedir ) );
            }

            for ( Iterator i = build.getTestResources().iterator(); i.hasNext(); )
            {
                Resource resource = (Resource) i.next();

                resource.setDirectory( unalignFromBaseDirectory( resource.getDirectory(), basedir ) );
            }

            if ( build.getFilters() != null )
            {
                List filters = new ArrayList();
                for ( Iterator i = build.getFilters().iterator(); i.hasNext(); )
                {
                    String filter = (String) i.next();

                    filters.add( unalignFromBaseDirectory( filter, basedir ) );
                }
                build.setFilters( filters );
            }

            build.setOutputDirectory( unalignFromBaseDirectory( build.getOutputDirectory(), basedir ) );

            build.setTestOutputDirectory( unalignFromBaseDirectory( build.getTestOutputDirectory(), basedir ) );
        }
    }

    public String unalignFromBaseDirectory( String directory, File basedir )
    {
        String path = basedir.getPath();
        if ( directory.startsWith( path ) )
        {
            directory = directory.substring( path.length() + 1 ).replace( '\\', '/' );
        }
        return directory;
    }

}

