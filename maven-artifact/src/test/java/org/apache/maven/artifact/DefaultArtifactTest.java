package org.apache.maven.artifact;

/*
 * Copyright 2001-2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import junit.framework.TestCase;

import org.apache.maven.artifact.handler.MockArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;

public class DefaultArtifactTest
    extends TestCase
{

    private DefaultArtifact artifact;

    private String groupId = "groupid", artifactId = "artifactId", version = "1.0", scope = "scope", type = "type",
        classifier = "classifier";

    private VersionRange versionRange;

    private MockArtifactHandler artifactHandler;

    protected void setUp()
        throws Exception
    {
        super.setUp();
        artifactHandler = new MockArtifactHandler();
        versionRange = VersionRange.createFromVersion( version );
        artifact = new DefaultArtifact( groupId, artifactId, versionRange, scope, type, classifier, artifactHandler );
    }

    public void testGetDependencyConflictId()
    {
        assertEquals( groupId + ":" + artifactId + ":" + type + ":" + classifier, artifact.getDependencyConflictId() );
    }

    public void testGetDependencyConflictIdNullGroupId()
    {
        artifact.setGroupId( null );
        assertEquals( null + ":" + artifactId + ":" + type + ":" + classifier, artifact.getDependencyConflictId() );
    }

    public void testGetDependencyConflictIdNullClassifier()
    {
        artifact = new DefaultArtifact( groupId, artifactId, versionRange, scope, type, null, artifactHandler );
        assertEquals( groupId + ":" + artifactId + ":" + type, artifact.getDependencyConflictId() );
    }

    public void testGetDependencyConflictIdNullScope()
    {
        artifact.setScope( null );
        assertEquals( groupId + ":" + artifactId + ":" + type + ":" + classifier, artifact.getDependencyConflictId() );
    }

    public void testToString()
    {
        assertEquals( groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version + ":" + scope,
                      artifact.toString() );
    }

    public void testToStringNullGroupId()
    {
        artifact.setGroupId( null );
        assertEquals( artifactId + ":" + type + ":" + classifier + ":" + version + ":" + scope, artifact.toString() );
    }

    public void testToStringNullClassifier()
    {
        artifact = new DefaultArtifact( groupId, artifactId, versionRange, scope, type, null, artifactHandler );
        assertEquals( groupId + ":" + artifactId + ":" + type + ":" + version + ":" + scope, artifact.toString() );
    }

    public void testToStringNullScope()
    {
        artifact.setScope( null );
        assertEquals( groupId + ":" + artifactId + ":" + type + ":" + classifier + ":" + version, artifact.toString() );
    }

}
