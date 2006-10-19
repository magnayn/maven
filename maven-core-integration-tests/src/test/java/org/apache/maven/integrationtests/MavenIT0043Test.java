package org.apache.maven.integrationtests;
import java.io.*;
import java.util.*;

import junit.framework.*;

import org.apache.maven.it.*;
import org.apache.maven.it.util.*;

public class MavenIT0043Test extends TestCase /*extends AbstractMavenIntegrationTest*/ {    

/** Test for repository inheritence - ensure using the same id overrides the defaults */
public void testit0043() throws Exception {
String basedir = System.getProperty("maven.test.tmpdir", System.getProperty("java.io.tmpdir"));
File testDir = new File(basedir, getName());
FileUtils.deleteDirectory(testDir);
System.out.println("Extracting it0043 to " + testDir.getAbsolutePath());
ResourceExtractor.extractResourcePath(getClass(), "/it0043", testDir);
Verifier verifier = new Verifier(testDir.getAbsolutePath());
verifier.executeGoal("package");
verifier.assertFilePresent("target/maven-it0043-1.0-SNAPSHOT.jar");
verifier.verifyErrorFreeLog();
verifier.resetStreams();
System.out.println("PASS");
}}
