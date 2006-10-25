package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenIT0098Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test that quoted system properties are processed correctly. [MNG-1415]
     */
    public void testit0098()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0098" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        List cliOptions = new ArrayList();
        cliOptions.add( "-Dtest.property=\"Test Property\"" );
        verifier.setCliOptions( cliOptions );
        verifier.executeGoal( "test" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0098 PASS" );
    }
}

