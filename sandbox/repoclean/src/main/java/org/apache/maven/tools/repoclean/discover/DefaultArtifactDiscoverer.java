package org.apache.maven.tools.repoclean.discover;

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
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.tools.repoclean.report.PathLister;
import org.apache.maven.tools.repoclean.report.ReportWriteException;
import org.apache.maven.tools.repoclean.report.Reporter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author jdcasey
 */
public class DefaultArtifactDiscoverer
    extends AbstractArtifactDiscoverer
{

    private ArtifactFactory artifactFactory;

    public List discoverArtifacts( File repositoryBase, Reporter reporter, String blacklistedPatterns,
                                   PathLister excludeLister, PathLister kickoutLister, boolean convertSnapshots )
        throws ReportWriteException
    {
        List artifacts = new ArrayList();

        String[] artifactPaths = scanForArtifactPaths( repositoryBase, blacklistedPatterns, excludeLister );

        for ( int i = 0; i < artifactPaths.length; i++ )
        {
            String path = artifactPaths[i];

            Artifact artifact = buildArtifact( path, reporter );

            if ( artifact != null )
            {
                if ( convertSnapshots || !artifact.isSnapshot() )
                {
                    artifacts.add( artifact );
                }
            }
        }

        return artifacts;
    }

    private Artifact buildArtifact( String path, Reporter reporter )
        throws ReportWriteException
    {
        Artifact result;

        List pathParts = new ArrayList();
        StringTokenizer st = new StringTokenizer( path, "/" );
        while ( st.hasMoreTokens() )
        {
            pathParts.add( st.nextToken() );
        }

        Collections.reverse( pathParts );

        if ( pathParts.size() < 4 )
        {
            reporter.error( "Not enough parts (4) in path " + path );
            return null;
        }

        //discard the actual artifact filename.
        pathParts.remove( 0 );

        // the next one is the version.
        String version = (String) pathParts.get( 0 );
        pathParts.remove( 0 );

        // the next one is the artifactId.
        String artifactId = (String) pathParts.get( 0 );
        pathParts.remove( 0 );

        // the remaining are the groupId.
        StringBuffer groupBuffer = new StringBuffer();

        boolean firstPart = true;
        for ( Iterator it = pathParts.iterator(); it.hasNext(); )
        {
            String part = (String) it.next();

            groupBuffer.append( part );

            if ( firstPart )
            {
                firstPart = false;
            }
            else if ( it.hasNext() )
            {
                groupBuffer.append( "." );
            }
        }

        result = artifactFactory.createArtifact( groupBuffer.toString(), artifactId, version, Artifact.SCOPE_RUNTIME,
                                                 "jar" );

        return result;
    }

}