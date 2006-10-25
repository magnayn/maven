package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0087Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Verify that a project-level plugin dependency class can be loaded from both the plugin classloader
     * and the context classloader available to the plugin.
     */
    public void testit0087()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0087" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.deleteArtifact( "org.apache.maven.plugins", "maven-it-it-plugin", "1.0", "maven-plugin" );
        verifier.executeGoal( "validate" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0087 PASS" );
    }
}

