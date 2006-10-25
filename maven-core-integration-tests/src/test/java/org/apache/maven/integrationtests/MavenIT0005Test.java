package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0005Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * The simplest of pom installation. We have a snapshot pom and we install
     * it in local repository.
     */
    public void testit0005()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0005" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.deleteArtifact( "org.apache.maven", "maven-it-it0005", "1.0-SNAPSHOT", "pom" );
        verifier.executeGoal( "install:install" );
        verifier.assertArtifactPresent( "org.apache.maven.its.it0005", "maven-it-it0005", "1.0-SNAPSHOT", "pom" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0005 PASS" );
    }
}

