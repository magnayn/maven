package org.apache.maven.integrationtests;
import java.io.*;
import java.util.*;

import junit.framework.*;

import org.apache.maven.it.*;
import org.apache.maven.it.util.*;

public class MavenIT0036Test extends TestCase /*extends AbstractMavenIntegrationTest*/ {    

/** Test building from release-pom.xml when it's available */
public void testit0036() throws Exception {
String basedir = System.getProperty("maven.test.tmpdir", System.getProperty("java.io.tmpdir"));
File testDir = new File(basedir, getName());
FileUtils.deleteDirectory(testDir);
System.out.println("Extracting it0036 to " + testDir.getAbsolutePath());
ResourceExtractor.extractResourcePath(getClass(), "/it0036", testDir);
Verifier verifier = new Verifier(testDir.getAbsolutePath());
verifier.executeGoal("package");
verifier.assertFilePresent("target/maven-core-it0036-1.0.jar");
verifier.verifyErrorFreeLog();
verifier.resetStreams();
System.out.println("PASS");
}}
