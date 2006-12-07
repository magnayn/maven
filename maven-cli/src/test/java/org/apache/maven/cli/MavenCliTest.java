package org.apache.maven.cli;

/*
 * Copyright 2006 The Apache Software Foundation.
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

import java.io.OutputStream;
import java.io.PrintStream;

import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.util.StringOutputStream;

import junit.framework.TestCase;

/**
 * Test method for 'org.apache.maven.cli.MavenCli.main(String[], ClassWorld)'
 *
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 * @version $Id$
 */
public class MavenCliTest
    extends TestCase
{
    /**
     * Test that JDK 1.4 or above is required to execute MavenCli
     *
     * @throws Exception
     */
    public void testMain()
        throws Exception
    {
        ClassWorld classWorld = new ClassWorld();

        PrintStream oldErr = System.err;
        PrintStream oldOut = System.out;

        OutputStream errOS = new StringOutputStream();
        PrintStream err = new PrintStream( errOS );
        System.setErr( err );
        OutputStream outOS = new StringOutputStream();
        PrintStream out = new PrintStream( outOS );
        System.setOut( out );

        try
        {
            System.setProperty( "java.specification.version", "1.0" );
            assertEquals( 1, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.1" );
            assertEquals( 1, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.2" );
            assertEquals( 1, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.3" );
            assertEquals( 1, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.4" );
            assertEquals( 0, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.5" );
            assertEquals( 0, MavenCli.main( new String[] { "-h" }, classWorld ) );
            System.setProperty( "java.specification.version", "1.6" );
            assertEquals( 0, MavenCli.main( new String[] { "-h" }, classWorld ) );
        }
        finally
        {
            System.setErr( oldErr );
            System.setErr( oldOut );
        }
    }
}
