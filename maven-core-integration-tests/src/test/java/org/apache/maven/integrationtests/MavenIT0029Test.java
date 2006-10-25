package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0029Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test for pluginManagement injection of plugin configuration.
     */
    public void testit0029()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0029" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.deleteArtifact( "org.apache.maven.it", "maven-it-it0029", "1.0-SNAPSHOT", "jar" );
        verifier.deleteArtifact( "org.apache.maven.it", "maven-it-it0029-child", "1.0-SNAPSHOT", "jar" );
        verifier.executeGoal( "install" );
        verifier.assertFilePresent( "child-project/target/classes/org/apache/maven/it0001/Person.class" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0029 PASS" );
    }
}

