package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0094Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test classloading issues with mojos after 2.0 (MNG-1898).
     */
    public void testit0094()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0094" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "install" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0094 PASS" );
    }
}

