package org.apache.maven.project.build.model;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.context.BuildContextManager;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.build.ProjectBuildCache;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DefaultModelLineageBuilderTest
    extends PlexusTestCase
{

    private DefaultModelLineageBuilder modelLineageBuilder;

    private ArtifactRepositoryLayout defaultLayout;

    private Set toDelete = new HashSet();

    public void setUp()
        throws Exception
    {
        super.setUp();
        getContainer().getLoggerManager().setThresholds( Logger.LEVEL_DEBUG );

        modelLineageBuilder = (DefaultModelLineageBuilder) lookup(
                                                                   ModelLineageBuilder.ROLE,
                                                                   DefaultModelLineageBuilder.ROLE_HINT );

        defaultLayout = (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, "default" );
    }

    public void tearDown()
        throws Exception
    {
        BuildContextManager ctxMgr = (BuildContextManager) lookup( BuildContextManager.ROLE );
        ctxMgr.clearBuildContext();

        super.tearDown();

        for ( Iterator it = toDelete.iterator(); it.hasNext(); )
        {
            File f = (File) it.next();

            if ( f.exists() )
            {
                FileUtils.forceDelete( f );
            }
        }
    }

    public void testShouldReadSinglePomWithNoParents()
        throws IOException, ProjectBuildingException
    {
        String groupId = "groupId";
        String artifactId = "artifactId";
        String version = "1.0";

        Model model = new Model();

        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );

        File pomFile = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        pomFile.deleteOnExit();

        FileWriter writer = null;
        try
        {
            writer = new FileWriter( pomFile );

            new MavenXpp3Writer().write( writer, model );
        }
        finally
        {
            IOUtil.close( writer );
        }

        ModelLineage lineage = modelLineageBuilder.buildModelLineage( pomFile, null, null, null,
                                                                      false );

        assertEquals( 1, lineage.size() );

        assertEquals( model.getId(), lineage.getOriginatingModel().getId() );
    }

    public void testReadPOMWithTwoAncestorsInLocalRepository()
        throws IOException, ProjectBuildingException
    {
        // 1. create local repository directory
        File localRepoDirectory = File.createTempFile(
                                                       "DefaultModelLineageBuilder.localRepository.",
                                                       "" );

        localRepoDirectory.delete();
        localRepoDirectory.mkdirs();

        deleteDirOnExit( localRepoDirectory );

        // 2. create and write the ancestor model to the local repo directory

        Model ancestor = createModel( "group", "ancestor", "1" );
        writeModel( ancestor, new File( localRepoDirectory, "group/ancestor/1/ancestor-1.pom" ) );

        // 3. create the parent model with a parent-ref to the ancestor model

        Model parent = createModel( "group", "parent", "1" );

        Parent parentParent = new Parent();
        parentParent.setGroupId( "group" );
        parentParent.setArtifactId( "ancestor" );
        parentParent.setVersion( "1" );

        parent.setParent( parentParent );

        // 4. write the parent model to the local repo directory
        writeModel( parent, new File( localRepoDirectory, "group/parent/1/parent-1.pom" ) );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "current", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );

        current.setParent( currentParent );

        // 6. write the current pom somewhere
        File currentPOM = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        currentPOM.deleteOnExit();

        writeModel( current, currentPOM );

        // 7. build the lineage.
        ArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                            "local",
                                                                            localRepoDirectory.toURL()
                                                                                              .toExternalForm(),
                                                                            defaultLayout );

        ModelLineage lineage = modelLineageBuilder.buildModelLineage( currentPOM, localRepository,
                                                                      Collections.EMPTY_LIST, null,
                                                                      false );

        assertEquals( 3, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( parent.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( ancestor.getId(), ( (Model) modelIterator.next() ).getId() );
    }

    public void testReadPOMWithMissingParentAndAllowStubsSetToTrue()
        throws IOException, ProjectBuildingException
    {
        // 1. create local repository directory
        File localRepoDirectory = File.createTempFile(
                                                       "DefaultModelLineageBuilder.localRepository.",
                                                       "" );

        localRepoDirectory.delete();
        localRepoDirectory.mkdirs();

        deleteDirOnExit( localRepoDirectory );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "current", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );

        current.setParent( currentParent );

        // 6. write the current pom somewhere
        File currentPOM = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        currentPOM.deleteOnExit();

        writeModel( current, currentPOM );

        // 7. build the lineage.
        ArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                            "local",
                                                                            localRepoDirectory.toURL()
                                                                                              .toExternalForm(),
                                                                            defaultLayout );

        ModelLineage lineage = modelLineageBuilder.buildModelLineage( currentPOM, localRepository,
                                                                      Collections.EMPTY_LIST, null,
                                                                      true );

        assertEquals( 2, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );

        Model parent = (Model) modelIterator.next();
        assertEquals( currentParent.getGroupId(), parent.getGroupId() );
        assertEquals( currentParent.getArtifactId(), parent.getArtifactId() );
        assertEquals( currentParent.getVersion(), parent.getVersion() );
    }

    public void testReadPOMWithParentInLocalRepositoryAndAncestorInRemoteRepository()
        throws IOException, ProjectBuildingException
    {
        // 1. create local and remote repository directories
        File localRepoDirectory = File.createTempFile(
                                                       "DefaultModelLineageBuilder.localRepository.",
                                                       "" );

        localRepoDirectory.delete();
        localRepoDirectory.mkdirs();

        deleteDirOnExit( localRepoDirectory );

        File remoteRepoDirectory = File.createTempFile(
                                                        "DefaultModelLineageBuilder.remoteRepository.",
                                                        "" );

        remoteRepoDirectory.delete();
        remoteRepoDirectory.mkdirs();

        deleteDirOnExit( remoteRepoDirectory );

        // 2. create and write the ancestor model to the local repo directory

        Model ancestor = createModel( "group", "ancestor", "1" );
        writeModel( ancestor, new File( remoteRepoDirectory, "group/ancestor/1/ancestor-1.pom" ) );

        // 3. create the parent model with a parent-ref to the ancestor model

        Model parent = createModel( "group", "parent", "1" );

        Parent parentParent = new Parent();
        parentParent.setGroupId( "group" );
        parentParent.setArtifactId( "ancestor" );
        parentParent.setVersion( "1" );

        parent.setParent( parentParent );

        // 4. write the parent model to the local repo directory
        writeModel( parent, new File( localRepoDirectory, "group/parent/1/parent-1.pom" ) );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "current", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );

        current.setParent( currentParent );

        // 6. write the current pom somewhere
        File currentPOM = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        currentPOM.deleteOnExit();

        writeModel( current, currentPOM );

        // 7. build the lineage.
        ArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                            "local",
                                                                            localRepoDirectory.toURL()
                                                                                              .toExternalForm(),
                                                                            defaultLayout );

        ArtifactRepository remoteRepository = new DefaultArtifactRepository(
                                                                             "test",
                                                                             remoteRepoDirectory.toURL()
                                                                                                .toExternalForm(),
                                                                             defaultLayout );

        ModelLineage lineage = modelLineageBuilder.buildModelLineage(
                                                                      currentPOM,
                                                                      localRepository,
                                                                      Collections.singletonList( remoteRepository ),
                                                                      null, false );

        assertEquals( 3, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( parent.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( ancestor.getId(), ( (Model) modelIterator.next() ).getId() );
    }

    public void testReadPOMWithParentInSiblingDirectoryUsingSpecifiedRelativePathThatIsADirectory()
        throws IOException, ProjectBuildingException
    {
        // 1. create project-root directory.
        File projectRootDirectory = File.createTempFile(
                                                         "DefaultModelLineageBuilder.projectRootDir.",
                                                         "" );

        projectRootDirectory.delete();
        projectRootDirectory.mkdirs();

        deleteDirOnExit( projectRootDirectory );

        // 2. create dir for parent POM within project root directory.
        File parentDir = new File( projectRootDirectory, "parent" );
        parentDir.mkdirs();

        // 2. create dir for child project within project root directory.
        File childDir = new File( projectRootDirectory, "child" );
        childDir.mkdirs();

        // 3. create the parent model in the parent-POM directory
        Model parent = createModel( "group", "parent", "1" );

        writeModel( parent, new File( parentDir, "pom.xml" ) );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "child", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );
        currentParent.setRelativePath( "../parent" );

        current.setParent( currentParent );

        // 6. write the current POM to the child directory
        File currentPOM = new File( childDir, "pom.xml" );
        writeModel( current, currentPOM );

        // 7. build the lineage.
        ArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                            "local",
                                                                            projectRootDirectory.toURL()
                                                                                                .toExternalForm(),
                                                                            defaultLayout );

        ModelLineage lineage = modelLineageBuilder.buildModelLineage( currentPOM, localRepository,
                                                                      Collections.EMPTY_LIST, null,
                                                                      false );

        assertEquals( 2, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( parent.getId(), ( (Model) modelIterator.next() ).getId() );
    }

    public void testReadPOMWithParentInRepoBroughtInViaSettingsProfile()
        throws IOException, ProjectBuildingException
    {
        // 1. create project-root directory.
        File projectRootDirectory = createTempDir( "projectRootDir" );

        // 1. create project-root directory.
        File repoRootDirectory = createTempDir( "repoRootDir" );

        File localRepoRootDirectory = createTempDir( "localRepoRootDir" );

        // 2. create dir for parent POM within project root directory.
        File parentDir = new File( repoRootDirectory, "group/parent/1" );
        parentDir.mkdirs();

        // 3. create the parent model in the parent-POM directory
        Model parent = createModel( "group", "parent", "1" );

        writeModel( parent, new File( parentDir, "parent-1.pom" ) );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "child", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );

        current.setParent( currentParent );

        // 6. write the current POM to the child directory
        File currentPOM = new File( projectRootDirectory, "pom.xml" );
        writeModel( current, currentPOM );

        // 7. build the lineage.
        ArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                            "local",
                                                                            localRepoRootDirectory.toURL()
                                                                                                .toExternalForm(),
                                                                            defaultLayout );

        Profile profile = new Profile();
        profile.setId( "external" );

        Repository repository = new Repository();
        repository.setId( "temp" );
        repository.setUrl( repoRootDirectory.toURL().toExternalForm() );

        profile.addRepository( repository );

        ProfileManager profileManager = new DefaultProfileManager( getContainer() );
        profileManager.addProfile( profile );
        profileManager.explicitlyActivate( profile.getId() );

        ModelLineage lineage = modelLineageBuilder.buildModelLineage( currentPOM, localRepository,
                                                                      Collections.EMPTY_LIST, profileManager,
                                                                      false );

        assertEquals( 2, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( parent.getId(), ( (Model) modelIterator.next() ).getId() );
    }

    private File createTempDir( String basename )
        throws IOException
    {
        File dir = File.createTempFile( "DefaultModelLineageBuilder." + basename + ".", "" );

        dir.delete();
        dir.mkdirs();

        deleteDirOnExit( dir );
        return dir;
    }

    private void deleteDirOnExit( File f )
    {
        toDelete.add( f );
    }

    private void writeModel( Model model,
                             File file )
        throws IOException
    {
        FileWriter writer = null;
        try
        {
            file.getParentFile().mkdirs();

            writer = new FileWriter( file );
            new MavenXpp3Writer().write( writer, model );

            writer.flush();
        }
        finally
        {
            IOUtil.close( writer );
        }

        System.out.println( "Verifying that: " + file.getAbsolutePath() + " exists: "
                            + file.exists() );
    }

    public void testReadPOMWithParentInOtherLocalFileWithBadRelativePath()
        throws Exception
    {
        // 1. create the parent model in a "local" POM file.
        File parentPOM = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        parentPOM.deleteOnExit();

        Model parent = createModel( "group", "parent", "1" );

        // 4. write the parent model to the local repo directory
        writeModel( parent, parentPOM );

        BuildContextManager buildContextManager = (BuildContextManager) lookup(
                                                                                BuildContextManager.ROLE,
                                                                                "default" );

        ProjectBuildCache cache = ProjectBuildCache.read( buildContextManager );
        cache.cacheModelFileForModel( parentPOM, parent );
        cache.store( buildContextManager );

        // 5. create the current pom with a parent-ref on the parent model
        Model current = createModel( "group", "current", "1" );

        Parent currentParent = new Parent();
        currentParent.setGroupId( "group" );
        currentParent.setArtifactId( "parent" );
        currentParent.setVersion( "1" );
        currentParent.setRelativePath( "../parent/pom.xml" );

        current.setParent( currentParent );

        // 6. write the current pom somewhere
        File currentPOM = File.createTempFile( "DefaultModelLineageBuilder.test.", ".pom" );
        currentPOM.deleteOnExit();

        writeModel( current, currentPOM );

        // 7. build the lineage.
        ModelLineage lineage = modelLineageBuilder.buildModelLineage( currentPOM, null,
                                                                      Collections.EMPTY_LIST, null,
                                                                      false );

        assertEquals( 2, lineage.size() );

        Iterator modelIterator = lineage.modelIterator();

        assertEquals( current.getId(), ( (Model) modelIterator.next() ).getId() );
        assertEquals( parent.getId(), ( (Model) modelIterator.next() ).getId() );
    }

    private Model createModel( String groupId,
                               String artifactId,
                               String version )
    {
        Model model = new Model();
        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );

        return model;
    }

}
