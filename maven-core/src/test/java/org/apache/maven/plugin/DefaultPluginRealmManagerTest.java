package org.apache.maven.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.codehaus.plexus.PlexusTestCase;

public class DefaultPluginRealmManagerTest
    extends PlexusTestCase
{
    public void testCreateComponent()
        throws Exception
    {
        assertNotNull( "Cannot lookup component: " + PluginRealmManager.ROLE, lookup( PluginRealmManager.ROLE ) );
    }

    public void testGenerateJarsListForArtifactsEmpty()
        throws Exception
    {
        DefaultPluginRealmManager defaultPluginRealmManager = new DefaultPluginRealmManager();

        assertEquals( "List of jars generated by empty set should be empty", 0,
                      defaultPluginRealmManager.generateJarsListForArtifacts( Collections.EMPTY_SET ).size() );
    }

    public void testGenerateJarsListForArtifacts()
        throws Exception
    {
        DefaultPluginRealmManager defaultPluginRealmManager = new DefaultPluginRealmManager();

        /*Different artifactId*/
        List/* <Artifact> */result =
            defaultPluginRealmManager.generateJarsListForArtifacts( createArtifactSet( HashSet.class, 5 ) );
        checkListOfNumeredFiles( 5, result );
        
        /*Different groups*/
        result =
            defaultPluginRealmManager.generateJarsListForArtifacts( createArtifactSetGroups( HashSet.class, 6 ) );
        checkListOfNumeredFiles( 6, result );
        
        /*Different versions*/
        result =
            defaultPluginRealmManager.generateJarsListForArtifacts( createArtifactSetVersions( HashSet.class, 4 ) );
        checkListOfNumeredFiles( 4, result );
    }

    public void testGetHashOfArtifacts() throws Exception
    {
        assertEquals("Hash code of empty set should be always the same", DefaultPluginRealmManager.getHashOfArtifacts( Collections.EMPTY_SET), DefaultPluginRealmManager.getHashOfArtifacts( Collections.EMPTY_SET));
        assertEquals("Hash code should be the same for the same content of artifacts list",DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(HashSet.class,5)),DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(HashSet.class,5)));
        assertEquals("Hash code should not depend on set implementation",DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(HashSet.class,5)),DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(TreeSet.class,5)));
    
        assertTrue( "Hash should be different for different content",  DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(HashSet.class,5))!=DefaultPluginRealmManager.getHashOfArtifacts(createArtifactSet(TreeSet.class,6)));
    }
    

    // =========================== Helpers ====================================================

    private void checkListOfNumeredFiles( int count, List/* <File> */result )
        throws MalformedURLException
    {
        assertEquals( "Unexpected size of created urls list: ", count, result.size() );
        for ( int i = 0; i < count; i++ )
        {
            File expectedFile = createNumberedFile( i );
            assertTrue( "Expected url not found: " + expectedFile + " in: " + result, result.contains( expectedFile ) );
        }
    }

    protected Artifact createArtifact( String groupId, String artifactId, String version )
        throws Exception
    {
        ArtifactFactory artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );

        // TODO: used to be SCOPE_COMPILE, check
        return artifactFactory.createBuildArtifact( groupId, artifactId, version, "jar" );
    }

    protected Set/* <Artifact> */createArtifactSet( Class/* <? extends Set> */setClass, int count )
        throws Exception
    {
        Set/* <Artifact> */artifactSet = (Set) setClass.newInstance();
        for ( int i = 0; i < count; i++ )
        {
            Artifact artifact = createArtifact( "testGroup", "testArtifact_" + i, "1.0" );
            artifact.setFile( createNumberedFile( i ) );
            artifactSet.add( artifact );
        }
        return artifactSet;
    }

    protected Set/* <Artifact> */createArtifactSetVersions( Class/* <? extends Set> */setClass, int count )
        throws Exception
    {
        Set/* <Artifact> */artifactSet = (Set) setClass.newInstance();
        for ( int i = 0; i < count; i++ )
        {
            Artifact artifact = createArtifact( "testGroup", "testArtifact", i + ".0" );
            artifact.setFile( createNumberedFile( i ) );
            artifactSet.add( artifact );
        }
        return artifactSet;
    }

    protected Set/* <Artifact> */createArtifactSetGroups( Class/* <? extends Set> */setClass, int count )
        throws Exception
    {
        Set/* <Artifact> */artifactSet = (Set) setClass.newInstance();
        for ( int i = 0; i < count; i++ )
        {
            Artifact artifact = createArtifact( "testGroup" + i, "testArtifact_", "1.0" );
            artifact.setFile( createNumberedFile( i ) );
            artifactSet.add( artifact );
        }
        return artifactSet;
    }

    protected File createNumberedFile( int i )
    {
        return new File( "/not_existing_file_" + i + ".jar" );
    }
}
