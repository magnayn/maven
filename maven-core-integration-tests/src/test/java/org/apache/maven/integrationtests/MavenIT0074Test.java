package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0074Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test that plugin-level configuration instances are not nullified by
     * execution-level configuration instances.
     */
    public void testit0074()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0074" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "eclipse:eclipse" );
        verifier.assertFilePresent( ".classpath" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0074 PASS" );
    }
}

