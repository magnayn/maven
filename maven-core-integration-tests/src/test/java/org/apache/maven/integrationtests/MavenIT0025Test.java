package org.apache.maven.integrationtests;
import java.io.*;
import java.util.*;

import junit.framework.*;

import org.apache.maven.it.*;
import org.apache.maven.it.util.*;

public class MavenIT0025Test extends TestCase /*extends AbstractMavenIntegrationTest*/ {    

/** Test multiple goal executions with different execution-level configs. */
public void testit0025() throws Exception {
String basedir = System.getProperty("maven.test.tmpdir", System.getProperty("java.io.tmpdir"));
File testDir = new File(basedir, getName());
FileUtils.deleteDirectory(testDir);
System.out.println("Extracting it0025 to " + testDir.getAbsolutePath());
ResourceExtractor.extractResourcePath(getClass(), "/it0025", testDir);
Verifier verifier = new Verifier(testDir.getAbsolutePath());
verifier.executeGoal("process-sources");
verifier.assertFilePresent("target/test.txt");
verifier.assertFilePresent("target/test2.txt");
verifier.verifyErrorFreeLog();
verifier.resetStreams();
System.out.println("PASS");
}}
