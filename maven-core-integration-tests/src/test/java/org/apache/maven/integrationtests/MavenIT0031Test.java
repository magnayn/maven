package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.util.Properties;

public class MavenIT0031Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test usage of plugins.xml mapping file on the repository to resolve
     * plugin artifactId from it's prefix using the pluginGroups in
     * the provided settings.xml.
     */
    public void testit0031()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0031" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        Properties systemProperties = new Properties();
        systemProperties.put( "org.apache.maven.user-settings", "settings.xml" );
        systemProperties.put( "model", "src/main/mdo/test.mdo" );
        systemProperties.put( "version", "1.0.0" );
        verifier.setSystemProperties( systemProperties );
        Properties verifierProperties = new Properties();
        verifierProperties.put( "failOnErrorOutput", "false" );
        verifier.setVerifierProperties( verifierProperties );
        verifier.executeGoal( "modello:java" );
        verifier.assertFilePresent( "target/generated-sources/modello/org/apache/maven/it/it0031/Root.java" );
// don't verify error free log
        verifier.resetStreams();
        System.out.println( "it0031 PASS" );
    }
}

