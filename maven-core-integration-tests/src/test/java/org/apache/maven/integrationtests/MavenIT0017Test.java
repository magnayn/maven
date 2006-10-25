package org.apache.maven.integrationtests;

import junit.framework.TestCase;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;

public class MavenIT0017Test
    extends TestCase /*extends AbstractMavenIntegrationTest*/
{

    /**
     * Test an EJB generation
     */
    public void testit0017()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/it0017" );
        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.executeGoal( "package" );
        verifier.assertFilePresent( "target/classes/org/apache/maven/it0017/Person.class" );
        verifier.assertFilePresent( "target/maven-it-it0017-1.0.jar" );
        verifier.assertFilePresent( "target/maven-it-it0017-1.0.jar!/META-INF/ejb-jar.xml" );
        verifier.assertFilePresent( "target/maven-it-it0017-1.0.jar!/org/apache/maven/it0017/Person.class" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        System.out.println( "it0017 PASS" );
    }
}

