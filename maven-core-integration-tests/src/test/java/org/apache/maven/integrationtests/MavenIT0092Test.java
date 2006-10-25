package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0092Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test that legacy repositories with legacy snapshots download correctly.
     */
    public void testit0092()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0092" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.deleteArtifact( "org.apache.maven", "maven-core-it-support", "1.0-SNAPSHOT", "jar" );
        verifier.executeGoal( "compile" );
        verifier.assertArtifactPresent( "org.apache.maven", "maven-core-it-support", "1.0-SNAPSHOT", "jar" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0092 PASS" );
    }
}

