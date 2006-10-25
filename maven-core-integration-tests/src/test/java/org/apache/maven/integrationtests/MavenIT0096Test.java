package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0096Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test that plugin executions from &gt;1 step of inheritance don't run multiple times.
     */
    public void testit0096()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0096" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "package" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0096 PASS" );
    }
}

