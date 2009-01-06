package org.apache.maven.mercury;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.mercury.artifact.ArtifactBasicMetadata;
import org.apache.maven.mercury.artifact.ArtifactMetadata;
import org.apache.maven.mercury.artifact.ArtifactScopeEnum;
import org.apache.maven.mercury.metadata.DependencyBuilder;
import org.apache.maven.mercury.metadata.DependencyBuilderFactory;
import org.apache.maven.mercury.metadata.MetadataTreeNode;
import org.apache.maven.mercury.repository.api.ArtifactBasicResults;
import org.apache.maven.mercury.repository.api.Repository;
import org.apache.maven.mercury.repository.api.RepositoryReader;
import org.apache.maven.mercury.repository.local.m2.LocalRepositoryM2;
import org.apache.maven.mercury.repository.remote.m2.RemoteRepositoryM2;
import org.apache.maven.mercury.spi.http.server.HttpTestServer;
import org.apache.maven.mercury.transport.api.Server;
import org.apache.maven.mercury.util.FileUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 *
 * @author Oleg Gusakov
 * @version $Id$
 *
 */
public class MavenDependencyProcessorTest
{
    LocalRepositoryM2 _localRepo;
    
    RemoteRepositoryM2 _remoteRepo;
    
    File _localRepoFile;

    static final String _remoteRepoDir = "./target/test-classes/repo";

    File _remoteRepoFile;

    static final String _remoteRepoUrlPrefix = "http://localhost:";

    static final String _remoteRepoUrlSufix = "/maven2";

    HttpTestServer _jetty;

    int _port;
    
    DependencyBuilder _depBuilder; 

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp()
    throws Exception
    {
        MavenDependencyProcessor dp = new MavenDependencyProcessor();
        
        _localRepoFile = File.createTempFile( "maven-mercury-", "-test-repo" );
        FileUtil.delete( _localRepoFile );
        _localRepoFile.mkdirs();
        _localRepoFile.deleteOnExit();
        _localRepo = new LocalRepositoryM2( "localRepo", _localRepoFile, dp );

        _remoteRepoFile = new File( _remoteRepoDir );
        _jetty = new HttpTestServer( _remoteRepoFile, _remoteRepoUrlSufix );
        _jetty.start();
        _port = _jetty.getPort();
        
        Server server = new Server( "testRemote", new URL(_remoteRepoUrlPrefix + _port + _remoteRepoUrlSufix) );
        _remoteRepo = new RemoteRepositoryM2( server, dp );
        
        ArrayList<Repository> repos = new ArrayList<Repository>(2);
        
        repos.add( _localRepo );
        repos.add( _remoteRepo );
        
        _depBuilder = DependencyBuilderFactory.create( DependencyBuilderFactory.JAVA_DEPENDENCY_MODEL, repos, null, null, null );
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown()
    throws Exception
    {
        if( _jetty != null )
        {
            _jetty.stop();
            _jetty.destroy();

            System.out.println( "Jetty on :" + _port + " destroyed\n<========\n\n" );
        }
    }

    /**
     * Test method for {@link org.apache.maven.mercury.MavenDependencyProcessor#getDependencies(org.apache.maven.mercury.artifact.ArtifactBasicMetadata, org.apache.maven.mercury.builder.api.MetadataReader, java.util.Map, java.util.Map)}.
     */
    @Test
    public void testMavenVersion()
    throws Exception
    {
        RepositoryReader rr = _remoteRepo.getReader();
        
        String gav = "org.apache.maven.plugins:maven-dependency-plugin:2.0";
        
        ArtifactBasicMetadata bmd = new ArtifactBasicMetadata( gav );
        ArrayList<ArtifactBasicMetadata> query = new ArrayList<ArtifactBasicMetadata>(1);
        query.add( bmd );
        
        ArtifactBasicResults res = rr.readDependencies( query );
        
        assertNotNull( res );
        
        assertFalse( res.hasExceptions() );
        
        assertTrue( res.hasResults() );
        
        List<ArtifactBasicMetadata> deps = res.getResult( bmd );
        
        assertNotNull( deps );
        
        assertFalse( deps.isEmpty() );
        
        System.out.println(deps);
        
        for( ArtifactBasicMetadata md : deps )
            if( "${maven.version}".equals( md.getVersion() ) )
                fail( "dependency has unresolved variable: "+md.toString() );
    }

}
