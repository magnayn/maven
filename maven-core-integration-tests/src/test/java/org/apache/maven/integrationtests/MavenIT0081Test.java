package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0081Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test per-plugin dependencies.
     */
    public void testit0081()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0081" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "install" );
        verifier.assertFilePresent( "test-component-c/target/org.apache.maven.wagon.providers.ftp.FtpWagon" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0081 PASS" );
    }
}

