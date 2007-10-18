package org.apache.maven.extension;

import org.apache.maven.ArtifactFilterManager;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenProjectSession;
import org.apache.maven.lifecycle.mapping.LifecycleMapping;
import org.apache.maven.model.Build;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultExtensionManagerTest
    extends PlexusTestCase
{

    private Set toDelete = new HashSet();

    private MutablePlexusContainer container;

    private ArtifactFilterManager filterManager;

    private ArtifactRepositoryFactory repoFactory;

    private ArtifactFactory factory;

    private ArtifactResolver resolver;

    private ArtifactMetadataSource metadataSource;

    private WagonManager wagonManager;

    public void setUp()
        throws Exception
    {
        super.setUp();

        container = (MutablePlexusContainer) getContainer();

//        container.getLoggerManager().setThreshold( Logger.LEVEL_DEBUG );

        filterManager = (ArtifactFilterManager) lookup( ArtifactFilterManager.class.getName() );

        repoFactory = (ArtifactRepositoryFactory) lookup( ArtifactRepositoryFactory.ROLE );

        factory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        resolver = (ArtifactResolver) lookup( ArtifactResolver.ROLE );

        metadataSource = (ArtifactMetadataSource) lookup( ArtifactMetadataSource.ROLE );

        wagonManager = (WagonManager) lookup( WagonManager.ROLE );
    }

    public void tearDown()
        throws Exception
    {
        for ( Iterator it = toDelete.iterator(); it.hasNext(); )
        {
            File f = (File) it.next();

            if ( f.exists() )
            {
                FileUtils.forceDelete( f );
            }
        }
    }

    public void test_addExtension_usingModel_ShouldLoadExtensionComponent()
        throws Exception
    {
        File remoteRepoDir = findRemoteRepositoryDirectory();
        File localRepo = createTempDir();

        Model model = createModel( "org.test", "artifact-name", "1" );
        Extension ext = addExtension( model, "org.apache.maven.core.test", "test-extension", "1" );

        List remoteRepositories = new ArrayList();
        remoteRepositories.add( repoFactory.createArtifactRepository( "central",
                                                                      remoteRepoDir.toURI()
                                                                                   .toURL()
                                                                                   .toExternalForm(),
                                                                      "default",
                                                                      null,
                                                                      null ) );

        DefaultArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                                   "local",
                                                                                   localRepo.getAbsolutePath(),
                                                                                   new DefaultRepositoryLayout() );
        localRepository.setBasedir( localRepo.getAbsolutePath() );

        ExtensionManager mgr = newDefaultExtensionManager();

        Map projectSessions = new HashMap();

        mgr.addExtension( ext, model, remoteRepositories, localRepository, projectSessions );

        MavenProjectSession projectSession = (MavenProjectSession) projectSessions.get( MavenProjectSession.createProjectId( model.getGroupId(),
                                                                                                                             model.getArtifactId(),
                                                                                                                             model.getVersion() ) );

        List compList = getContainer().getComponentDescriptorList( ArtifactFactory.ROLE,
                                                                   projectSession.getProjectRealm() );

        System.out.println( "Got: " + compList );

        ClassRealm oldRealm = getContainer().setLookupRealm( projectSession.getProjectRealm() );

        ArtifactFactory result = (ArtifactFactory) lookup( ArtifactFactory.ROLE, "test" );
        assertNotNull( result );

        getContainer().setLookupRealm( oldRealm );
    }

    public void test_addExtension_usingModel_ShouldLoadCustomLifecycleMappingAndArtifactHandler()
        throws Exception
    {
        File remoteRepoDir = findRemoteRepositoryDirectory();
        File localRepo = createTempDir();

        Model model = createModel( "org.test", "artifact-name", "1" );
        Extension ext = addExtension( model, "org.apache.maven.core.test", "test-lifecycle-and-artifactHandler", "1" );

        List remoteRepositories = new ArrayList();
        remoteRepositories.add( repoFactory.createArtifactRepository( "central",
                                                                      remoteRepoDir.toURI()
                                                                                   .toURL()
                                                                                   .toExternalForm(),
                                                                      "default",
                                                                      null,
                                                                      null ) );

        DefaultArtifactRepository localRepository = new DefaultArtifactRepository(
                                                                                   "local",
                                                                                   localRepo.getAbsolutePath(),
                                                                                   new DefaultRepositoryLayout() );
        localRepository.setBasedir( localRepo.getAbsolutePath() );

        ExtensionManager mgr = newDefaultExtensionManager();

        Map projectSessions = new HashMap();

        mgr.addExtension( ext, model, remoteRepositories, localRepository, projectSessions );

        MavenProjectSession projectSession = (MavenProjectSession) projectSessions.get( MavenProjectSession.createProjectId( model.getGroupId(),
                                                                                                                             model.getArtifactId(),
                                                                                                                             model.getVersion() ) );

        List lcCompList = getContainer().getComponentDescriptorList( LifecycleMapping.ROLE,
                                                                   projectSession.getProjectRealm() );

        System.out.println( "Got lifecyle mappings: " + lcCompList );

        List ahCompList = getContainer().getComponentDescriptorList( ArtifactHandler.ROLE,
                                                                   projectSession.getProjectRealm() );

        System.out.println( "Got artifact handlers: " + ahCompList );

        ClassRealm oldRealm = getContainer().setLookupRealm( projectSession.getProjectRealm() );

        LifecycleMapping lcResult = (LifecycleMapping) lookup( LifecycleMapping.ROLE, "test" );
        assertNotNull( lcResult );

        ArtifactHandler ahResult = (ArtifactHandler) lookup( ArtifactHandler.ROLE, "test" );
        assertNotNull( ahResult );

        getContainer().setLookupRealm( oldRealm );
    }

    private ExtensionManager newDefaultExtensionManager()
    {
        DefaultExtensionManager mgr = new DefaultExtensionManager( factory, resolver,
                                                                   metadataSource, container,
                                                                   filterManager, wagonManager );

        Logger logger = getContainer().getLoggerManager()
                                      .getLoggerForComponent( DefaultExtensionManager.class.getName() );

        mgr.enableLogging( logger );

        return mgr;
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

    private Extension addExtension( Model model,
                                    String groupId,
                                    String artifactId,
                                    String version )
    {
        Extension ext = new Extension();
        ext.setGroupId( groupId );
        ext.setArtifactId( artifactId );
        ext.setVersion( version );

        Build build = model.getBuild();
        if ( build == null )
        {
            build = new Build();
            model.setBuild( build );
        }

        build.addExtension( ext );

        return ext;
    }

    private File createTempDir()
        throws IOException
    {
        File dir = File.createTempFile( "DefaultExtensionManagerTest.", ".dir" );
        FileUtils.forceDelete( dir );

        dir.mkdirs();
        toDelete.add( dir );

        return dir;
    }

    private File findRemoteRepositoryDirectory()
    {
        String classPath = getClass().getPackage().getName().replace( '.', '/' )
                           + "/test-extension-repo/repo-marker.txt";
        ClassLoader cloader = Thread.currentThread().getContextClassLoader();

        URL resource = cloader.getResource( classPath );

        if ( resource == null )
        {
            throw new IllegalStateException( "Cannot find repository marker file: " + classPath
                                             + " in context classloader!" );
        }

        File repoDir = new File( resource.getPath() ).getParentFile();

        return repoDir;
    }

}
