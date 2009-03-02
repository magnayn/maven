package org.apache.maven.artifact.resolver;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.AbstractArtifactComponentTestCase;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;

// It would be cool if there was a hook that i could use to setup a test environment.
// I want to setup a local/remote repositories for testing but i don't want to have
// to change them when i change the layout of the repositories. So i want to generate
// the structure i want to test by using the artifact handler manager which dictates
// the layout used for a particular artifact type.

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
public class ArtifactResolverTest
    extends AbstractArtifactComponentTestCase
{
    private DefaultArtifactResolver artifactResolver;

    private Artifact projectArtifact;

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        artifactResolver = (DefaultArtifactResolver) lookup( ArtifactResolver.class );

        projectArtifact = createLocalArtifact( "project", "3.0" );
    }

    @Override
    protected String component()
    {
        return "resolver";
    }

    public void testResolutionOfASingleArtifactWhereTheArtifactIsPresentInTheLocalRepository()
        throws Exception
    {
        Artifact a = createLocalArtifact( "a", "1.0" );

        artifactResolver.resolve( a, remoteRepositories(), localRepository() );

        assertLocalArtifactPresent( a );
    }

    public void testResolutionOfASingleArtifactWhereTheArtifactIsNotPresentLocallyAndMustBeRetrievedFromTheRemoteRepository()
        throws Exception
    {
        Artifact b = createRemoteArtifact( "b", "1.0-SNAPSHOT" );
        deleteLocalArtifact( b );

        artifactResolver.resolve( b, remoteRepositories(), localRepository() );

        assertLocalArtifactPresent( b );
    }

    @Override
    protected Artifact createArtifact( String groupId, String artifactId, String version, String type )
        throws Exception
    {
        // for the anonymous classes
        return super.createArtifact( groupId, artifactId, version, type );
    }

    public void testTransitiveResolutionWhereAllArtifactsArePresentInTheLocalRepository()
        throws Exception
    {
        Artifact g = createLocalArtifact( "g", "1.0" );

        Artifact h = createLocalArtifact( "h", "1.0" );

        ArtifactMetadataSource mds = new ArtifactMetadataSource()
        {
            public ResolutionGroup retrieve( Artifact artifact, ArtifactRepository localRepository,
                                             List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                Set dependencies = new HashSet();

                if ( "g".equals( artifact.getArtifactId() ) )
                {
                    Artifact a = null;
                    try
                    {
                        a = createArtifact( "org.apache.maven", "h", "1.0", "jar" );
                        dependencies.add( a );
                    }
                    catch ( Exception e )
                    {
                        throw new ArtifactMetadataRetrievalException( "Error retrieving metadata", e, a );
                    }
                }

                return new ResolutionGroup( artifact, dependencies, remoteRepositories );
            }

            public List<ArtifactVersion> retrieveAvailableVersions( Artifact artifact,
                                                                    ArtifactRepository localRepository,
                                                                    List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public List<ArtifactVersion> retrieveAvailableVersionsFromDeploymentRepository(
                                                                                            Artifact artifact,
                                                                                            ArtifactRepository localRepository,
                                                                                            ArtifactRepository remoteRepository )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public Artifact retrieveRelocatedArtifact( Artifact artifact,
                                                       ArtifactRepository localRepository,
                                                       List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                return artifact;
            }
        };

        ArtifactResolutionResult result =
            artifactResolver.resolveTransitively( Collections.singleton( g ), projectArtifact, remoteRepositories(), localRepository(), mds );

        assertEquals( 2, result.getArtifacts().size() );

        assertTrue( result.getArtifacts().contains( g ) );

        assertTrue( result.getArtifacts().contains( h ) );

        assertLocalArtifactPresent( g );

        assertLocalArtifactPresent( h );
    }

    public void testTransitiveResolutionWhereAllArtifactsAreNotPresentInTheLocalRepositoryAndMustBeRetrievedFromTheRemoteRepository()
        throws Exception
    {
        Artifact i = createRemoteArtifact( "i", "1.0-SNAPSHOT" );
        deleteLocalArtifact( i );

        Artifact j = createRemoteArtifact( "j", "1.0-SNAPSHOT" );
        deleteLocalArtifact( j );

        ArtifactMetadataSource mds = new ArtifactMetadataSource()
        {
            public ResolutionGroup retrieve( Artifact artifact, ArtifactRepository localRepository,
                                             List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                Set dependencies = new HashSet();

                if ( "i".equals( artifact.getArtifactId() ) )
                {
                    Artifact a = null;
                    try
                    {
                        a = createArtifact( "org.apache.maven", "j", "1.0-SNAPSHOT", "jar" );
                        dependencies.add( a );
                    }
                    catch ( Exception e )
                    {
                        throw new ArtifactMetadataRetrievalException( "Error retrieving metadata", e, a );
                    }
                }

                return new ResolutionGroup( artifact, dependencies, remoteRepositories );
            }

            public List<ArtifactVersion> retrieveAvailableVersions( Artifact artifact,
                                                                    ArtifactRepository localRepository,
                                                                    List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public List<ArtifactVersion> retrieveAvailableVersionsFromDeploymentRepository(
                                                                                            Artifact artifact,
                                                                                            ArtifactRepository localRepository,
                                                                                            ArtifactRepository remoteRepository )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public Artifact retrieveRelocatedArtifact( Artifact artifact,
                                                       ArtifactRepository localRepository,
                                                       List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                return artifact;
            }
        };

        ArtifactResolutionResult result =
            artifactResolver.resolveTransitively( Collections.singleton( i ), projectArtifact, remoteRepositories(),
                                                  localRepository(), mds );

        assertEquals( 2, result.getArtifacts().size() );

        assertTrue( result.getArtifacts().contains( i ) );

        assertTrue( result.getArtifacts().contains( j ) );

        assertLocalArtifactPresent( i );

        assertLocalArtifactPresent( j );
    }

    public void testResolutionFailureWhenArtifactNotPresentInRemoteRepository()
        throws Exception
    {
        Artifact k = createArtifact( "k", "1.0" );

        try
        {
            artifactResolver.resolve( k, remoteRepositories(), localRepository() );
            fail( "Resolution succeeded when it should have failed" );
        }
        catch ( ArtifactNotFoundException expected )
        {
            assertTrue( true );
        }
    }

    public void testResolutionOfAnArtifactWhereOneRemoteRepositoryIsBadButOneIsGood()
        throws Exception
    {
        Artifact l = createRemoteArtifact( "l", "1.0-SNAPSHOT" );
        deleteLocalArtifact( l );

        List<ArtifactRepository> repositories = new ArrayList<ArtifactRepository>();
        repositories.add( remoteRepository() );
        repositories.add( badRemoteRepository() );

        artifactResolver.resolve( l, repositories, localRepository() );

        assertLocalArtifactPresent( l );
    }

    public void testTransitiveResolutionOrder()
        throws Exception
    {
        Artifact m = createLocalArtifact( "m", "1.0" );

        Artifact n = createLocalArtifact( "n", "1.0" );

        ArtifactMetadataSource mds = new ArtifactMetadataSource()
        {
            public ResolutionGroup retrieve( Artifact artifact, ArtifactRepository localRepository,
                                             List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                Set dependencies = new HashSet();

                return new ResolutionGroup( artifact, dependencies, remoteRepositories );
            }

            public List<ArtifactVersion> retrieveAvailableVersions( Artifact artifact,
                                                                    ArtifactRepository localRepository,
                                                                    List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public List<ArtifactVersion> retrieveAvailableVersionsFromDeploymentRepository(
                                                                                            Artifact artifact,
                                                                                            ArtifactRepository localRepository,
                                                                                            ArtifactRepository remoteRepository )
                throws ArtifactMetadataRetrievalException
            {
                throw new UnsupportedOperationException( "Cannot get available versions in this test case" );
            }

            public Artifact retrieveRelocatedArtifact( Artifact artifact,
                                                       ArtifactRepository localRepository,
                                                       List<ArtifactRepository> remoteRepositories )
                throws ArtifactMetadataRetrievalException
            {
                return artifact;
            }
        };

        ArtifactResolutionResult result = null;

        Set set = new LinkedHashSet();
        set.add( n );
        set.add( m );

        result =
            artifactResolver.resolveTransitively( set, projectArtifact, remoteRepositories(), localRepository(), mds );

        Iterator i = result.getArtifacts().iterator();
        assertEquals( "n should be first", n, i.next() );
        assertEquals( "m should be second", m, i.next() );

        // inverse order
        set = new LinkedHashSet();
        set.add( m );
        set.add( n );

        result =
            artifactResolver.resolveTransitively( set, projectArtifact, remoteRepositories(), localRepository(), mds );

        i = result.getArtifacts().iterator();
        assertEquals( "m should be first", m, i.next() );
        assertEquals( "n should be second", n, i.next() );
    }
}