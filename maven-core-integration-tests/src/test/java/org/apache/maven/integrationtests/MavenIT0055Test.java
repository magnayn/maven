package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0055Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test that source includes/excludes with in the compiler plugin config.
     * This will test excludes and testExcludes...
     */
    public void testit0055()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0055" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "test-compile" );
        verifier.assertFilePresent( "target/classes/org/apache/maven/it0001/Person.class" );
        verifier.assertFilePresent( "target/test-classes/org/apache/maven/it0001/PersonTest.class" );
        verifier.assertFileNotPresent( "target/classes/org/apache/maven/it0001/PersonTwo.class" );
        verifier.assertFileNotPresent( "target/test-classes/org/apache/maven/it0001/PersonTwoTest.class" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0055 PASS" );
    }
}

