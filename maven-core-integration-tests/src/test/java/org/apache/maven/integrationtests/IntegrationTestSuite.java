package org.apache.maven.integrationtests;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.framework.TestResult;

public class IntegrationTestSuite
    extends TestCase
{
    public static Test suite()
    {
        TestSuite suite = new MavenTestSuite();
        suite.addTestSuite( MavenIT0000Test.class );
        suite.addTestSuite( MavenIT0001Test.class );
        suite.addTestSuite( MavenIT0002Test.class );
        suite.addTestSuite( MavenIT0003Test.class );
        suite.addTestSuite( MavenIT0004Test.class );
        suite.addTestSuite( MavenIT0005Test.class );
        suite.addTestSuite( MavenIT0006Test.class );
        suite.addTestSuite( MavenIT0007Test.class );
        suite.addTestSuite( MavenIT0008Test.class );
        suite.addTestSuite( MavenIT0009Test.class );
        suite.addTestSuite( MavenIT0010Test.class );
        suite.addTestSuite( MavenIT0011Test.class );
        suite.addTestSuite( MavenIT0012Test.class );
        suite.addTestSuite( MavenIT0013Test.class );
        suite.addTestSuite( MavenIT0014Test.class );
        suite.addTestSuite( MavenIT0016Test.class );
        suite.addTestSuite( MavenIT0017Test.class );
        suite.addTestSuite( MavenIT0018Test.class );
        suite.addTestSuite( MavenIT0019Test.class );
        suite.addTestSuite( MavenIT0020Test.class );
        suite.addTestSuite( MavenIT0021Test.class );
        suite.addTestSuite( MavenIT0022Test.class );
        suite.addTestSuite( MavenIT0023Test.class );
        suite.addTestSuite( MavenIT0024Test.class );
        suite.addTestSuite( MavenIT0025Test.class );
        suite.addTestSuite( MavenIT0026Test.class );
        suite.addTestSuite( MavenIT0027Test.class );
        suite.addTestSuite( MavenIT0028Test.class );
        suite.addTestSuite( MavenIT0029Test.class );
        suite.addTestSuite( MavenIT0030Test.class );
        suite.addTestSuite( MavenIT0031Test.class );
        suite.addTestSuite( MavenIT0032Test.class );
        suite.addTestSuite( MavenIT0033Test.class );
        suite.addTestSuite( MavenIT0034Test.class );
        suite.addTestSuite( MavenIT0035Test.class );
        suite.addTestSuite( MavenIT0036Test.class );
        suite.addTestSuite( MavenIT0037Test.class );
        suite.addTestSuite( MavenIT0038Test.class );
        suite.addTestSuite( MavenIT0039Test.class );
        suite.addTestSuite( MavenIT0040Test.class );
        suite.addTestSuite( MavenIT0041Test.class );
        suite.addTestSuite( MavenIT0042Test.class );
        suite.addTestSuite(MavenIT0043Test.class);
        suite.addTestSuite( MavenIT0044Test.class );
        suite.addTestSuite( MavenIT0045Test.class );
        suite.addTestSuite( MavenIT0046Test.class );
        suite.addTestSuite( MavenIT0047Test.class );
        suite.addTestSuite( MavenIT0048Test.class );
        suite.addTestSuite( MavenIT0049Test.class );
        suite.addTestSuite( MavenIT0050Test.class );
        suite.addTestSuite( MavenIT0051Test.class );
        suite.addTestSuite( MavenIT0052Test.class );
        suite.addTestSuite( MavenIT0053Test.class );
        suite.addTestSuite( MavenIT0054Test.class );
        suite.addTestSuite( MavenIT0055Test.class );
        suite.addTestSuite( MavenIT0056Test.class );
        suite.addTestSuite( MavenIT0057Test.class );
        suite.addTestSuite( MavenIT0058Test.class );
        suite.addTestSuite( MavenIT0059Test.class );
        suite.addTestSuite( MavenIT0060Test.class );
        suite.addTestSuite( MavenIT0061Test.class );
        suite.addTestSuite( MavenIT0062Test.class );
        suite.addTestSuite( MavenIT0063Test.class );
        suite.addTestSuite( MavenIT0064Test.class );
        suite.addTestSuite( MavenIT0065Test.class );
        suite.addTestSuite( MavenIT0066Test.class );
        suite.addTestSuite( MavenIT0067Test.class );
        suite.addTestSuite( MavenIT0068Test.class );
        suite.addTestSuite( MavenIT0069Test.class );
        suite.addTestSuite( MavenIT0070Test.class );
        suite.addTestSuite( MavenIT0071Test.class );
        suite.addTestSuite( MavenIT0072Test.class );
        suite.addTestSuite( MavenIT0073Test.class );
        suite.addTestSuite( MavenIT0074Test.class );
        suite.addTestSuite( MavenIT0075Test.class );
        suite.addTestSuite( MavenIT0076Test.class );
        suite.addTestSuite( MavenIT0077Test.class );
        suite.addTestSuite( MavenIT0078Test.class );
        suite.addTestSuite( MavenIT0079Test.class );
        suite.addTestSuite( MavenIT0080Test.class );
        suite.addTestSuite( MavenIT0081Test.class );
        suite.addTestSuite( MavenIT0082Test.class );
        suite.addTestSuite( MavenIT0083Test.class );
        suite.addTestSuite( MavenIT0084Test.class );
        suite.addTestSuite( MavenIT0085Test.class );
        suite.addTestSuite( MavenIT0086Test.class );
        suite.addTestSuite( MavenIT0087Test.class );
        suite.addTestSuite( MavenIT0088Test.class );
        suite.addTestSuite( MavenIT0089Test.class );
        // suite.addTestSuite(MavenIT0090Test.class);
        // suite.addTestSuite(MavenIT0091Test.class);
        suite.addTestSuite( MavenIT0092Test.class );
        suite.addTestSuite( MavenIT0094Test.class );
        suite.addTestSuite( MavenIT0095Test.class );
        suite.addTestSuite(MavenIT0096Test.class);
        suite.addTestSuite(MavenIT0097Test.class);
        // suite.addTestSuite(MavenIT0098Test.class);
        suite.addTestSuite( MavenIT0099Test.class );
        suite.addTestSuite( MavenIT0100Test.class );
        suite.addTestSuite( MavenIT0101Test.class );
        suite.addTestSuite( MavenIT0102Test.class );
        suite.addTestSuite( MavenIT0103Test.class );
        // suite.addTestSuite(MavenIT0104Test.class);
        suite.addTestSuite( MavenIT0105Test.class );
        // suite.addTestSuite(MavenIT0106Test.class);
        // suite.addTestSuite(MavenIT0107Test.class);
        return suite;
    }

    static class MavenTestSuite
        extends TestSuite
    {
        public void runTest( Test test, TestResult testResult )
        {
            String name = test.getClass().getName();
            System.out.println( "name = " + name );
            super.runTest( test, testResult );
        }
    }
}
