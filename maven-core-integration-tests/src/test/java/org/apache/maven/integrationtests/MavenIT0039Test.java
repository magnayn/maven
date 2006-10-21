package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.FileUtils;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenIT0039Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test reactor for projects that have release-pom.xml in addition to
     * pom.xml. The release-pom.xml file should be chosen above pom.xml for
     * these projects in the build.
     */
    public void testit0039()
        throws Exception
    {
        String basedir = System.getProperty( "maven.test.tmpdir", System.getProperty( "java.io.tmpdir" ) );
        File testDir = new File( basedir, getName() );
        FileUtils.deleteDirectory( testDir );
        System.out.println( "Extracting it0039 to " + testDir.getAbsolutePath() );
        ResourceExtractor.extractResourcePath( getClass(), "/it0039", testDir );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        FileUtils.deleteFile( new File( basedir, "project/target/maven-core-it0039-p1-1.0.jar" ) );
        FileUtils.deleteFile( new File( basedir, "project2/target/maven-core-it0039-p2-1.0.jar" ) );
        List cliOptions = new ArrayList();
        cliOptions.add( "-r" );
        verifier.setCliOptions( cliOptions );
        verifier.executeGoal( "package" );
        verifier.assertFilePresent( "project/target/maven-core-it0039-p1-1.0.jar" );
        verifier.assertFilePresent( "project2/target/maven-core-it0039-p2-1.0.jar" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "PASS" );
    }
}

