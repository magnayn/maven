package org.apache.maven.project;

/*
 * Copyright 2005 The Apache Software Foundation.
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

import java.io.File;
import java.io.IOException;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;

public class MavenProjectTest
    extends AbstractMavenProjectTestCase
{

    public void testShouldInterpretChildPathAdjustmentBasedOnModulePaths()
        throws IOException
    {
        Model parentModel = new Model();
        parentModel.addModule( "../child" );

        MavenProject parentProject = new MavenProject( parentModel );

        Model childModel = new Model();
        childModel.setArtifactId( "artifact" );

        MavenProject childProject = new MavenProject( childModel );
        
        File childFile = new File( System.getProperty( "java.io.tmpdir" ), "maven-project-tests" + System.currentTimeMillis() + "/child/pom.xml" );

        childProject.setFile( childFile );

        String adjustment = parentProject.getModulePathAdjustment( childProject );

        assertNotNull( adjustment );
        
        assertEquals( "..", adjustment );
    }

    public void testIdentityProtoInheritance()
    {
        Parent parent = new Parent();

        parent.setGroupId( "test-group" );
        parent.setVersion( "1000" );
        parent.setArtifactId( "test-artifact" );

        Model model = new Model();

        model.setParent( parent );
        model.setArtifactId( "real-artifact" );

        MavenProject project = new MavenProject( model );

        assertEquals( "groupId proto-inheritance failed.", "test-group", project.getGroupId() );
        assertEquals( "artifactId is masked.", "real-artifact", project.getArtifactId() );
        assertEquals( "version proto-inheritance failed.", "1000", project.getVersion() );

        // draw the NPE.
        project.getId();
    }

    public void testEmptyConstructor()
    {
        MavenProject project = new MavenProject();

        assertEquals( MavenProject.EMPTY_PROJECT_GROUP_ID + ":" + MavenProject.EMPTY_PROJECT_ARTIFACT_ID + ":jar:"
                        + MavenProject.EMPTY_PROJECT_VERSION, project.getId() );
    }

    public void testCopyConstructor()
        throws Exception
    {
        File f = getFileForClasspathResource( "canonical-pom.xml" );
        MavenProject projectToClone = getProject( f );

        MavenProject clonedProject = new MavenProject( projectToClone );
        assertEquals( "maven-core", clonedProject.getArtifactId() );
    }

    public void testGetModulePathAdjustment()
        throws IOException
    {
        Model moduleModel = new Model();

        MavenProject module = new MavenProject( moduleModel );
        module.setFile( new File( "module-dir/pom.xml" ) );

        Model parentModel = new Model();
        parentModel.addModule( "../module-dir" );

        MavenProject parent = new MavenProject( parentModel );
        parent.setFile( new File( "parent-dir/pom.xml" ) );

        String pathAdjustment = parent.getModulePathAdjustment( module );

        assertEquals( "..", pathAdjustment );
    }
}
