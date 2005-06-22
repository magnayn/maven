package org.apache.maven.artifact.resolver;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExclusionSetFilter;
import org.codehaus.plexus.PlexusTestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test the default artifact collector.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class DefaultArtifactCollectorTest
    extends PlexusTestCase
{
    private ArtifactCollector artifactCollector;

    private ArtifactFactory artifactFactory;

    private ArtifactSpec projectArtifact;

    private Source source;

    private static final String GROUP_ID = "test";

    protected void setUp()
        throws Exception
    {
        super.setUp();

        this.source = new Source();
        this.artifactFactory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        this.artifactCollector = new DefaultArtifactCollector();

        this.projectArtifact = createArtifact( "project", "1.0", null );
    }

    public void disabledtestCircularDependencyNotIncludingCurrentProject()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec b = a.addDependency( "b", "1.0" );
        b.addDependency( "a", "1.0" );
        try
        {
            collect( a );
            fail( "Should have failed on cyclic dependency not involving project" );
        }
        catch ( CyclicDependencyException expected )
        {
            assertTrue( true );
        }
    }

    public void disabledtestCircularDependencyIncludingCurrentProject()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec b = a.addDependency( "b", "1.0" );
        b.addDependency( "project", "1.0" );
        try
        {
            collect( a );
            fail( "Should have failed on cyclic dependency involving project" );
        }
        catch ( CyclicDependencyException expected )
        {
            assertTrue( true );
        }
    }

    public void testResolveWithFilter()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec b = a.addDependency( "b", "1.0" );
        ArtifactSpec c = a.addDependency( "c", "3.0" );

        b.addDependency( "c", "2.0" );
        ArtifactSpec d = b.addDependency( "d", "4.0" );

        ArtifactResolutionResult res = collect( a );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, b.artifact, c.artifact, d.artifact} ),
                      res.getArtifacts() );

        ArtifactFilter filter = new ExclusionSetFilter( new String[]{"b"} );
        res = collect( a, filter );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, c.artifact} ), res.getArtifacts() );
    }

    public void testResolveNearest()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec b = a.addDependency( "b", "1.0" );
        ArtifactSpec c = a.addDependency( "c", "3.0" );

        b.addDependency( "c", "2.0" );

        ArtifactResolutionResult res = collect( a );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, b.artifact, c.artifact} ),
                      res.getArtifacts() );
    }

    public void testResolveManagedVersion()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        a.addDependency( "b", "3.0", Artifact.SCOPE_RUNTIME );

        Artifact managedVersion = createArtifact( "b", "5.0" ).artifact;
        Artifact modifiedB = createArtifact( "b", "5.0", Artifact.SCOPE_RUNTIME ).artifact;

        ArtifactResolutionResult res = collect( a, managedVersion );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedB} ), res.getArtifacts() );
    }

    public void testResolveCompileScopeOverTestScope()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec c = createArtifact( "c", "3.0", Artifact.SCOPE_TEST );

        a.addDependency( "c", "2.0", Artifact.SCOPE_COMPILE );

        Artifact modifiedC = createArtifact( "c", "3.0", Artifact.SCOPE_COMPILE ).artifact;

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, c.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedC} ), res.getArtifacts() );
        Artifact artifact = getArtifact( "c", res.getArtifacts() );
        assertEquals( "Check scope", Artifact.SCOPE_COMPILE, artifact.getScope() );
    }

    public void testResolveRuntimeScopeOverTestScope()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec c = createArtifact( "c", "3.0", Artifact.SCOPE_TEST );

        a.addDependency( "c", "2.0", Artifact.SCOPE_RUNTIME );

        Artifact modifiedC = createArtifact( "c", "3.0", Artifact.SCOPE_RUNTIME ).artifact;

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, c.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedC} ), res.getArtifacts() );
        Artifact artifact = getArtifact( "c", res.getArtifacts() );
        assertEquals( "Check scope", Artifact.SCOPE_RUNTIME, artifact.getScope() );
    }

    public void testResolveCompileScopeOverRuntimeScope()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec c = createArtifact( "c", "3.0", Artifact.SCOPE_RUNTIME );

        a.addDependency( "c", "2.0", Artifact.SCOPE_COMPILE );

        Artifact modifiedC = createArtifact( "c", "3.0", Artifact.SCOPE_COMPILE ).artifact;

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, c.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedC} ), res.getArtifacts() );
        Artifact artifact = getArtifact( "c", res.getArtifacts() );
        assertEquals( "Check scope", Artifact.SCOPE_COMPILE, artifact.getScope() );
    }

    public void testResolveCompileScopeOverProvidedScope()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec c = createArtifact( "c", "3.0", Artifact.SCOPE_PROVIDED );

        a.addDependency( "c", "2.0", Artifact.SCOPE_COMPILE );

        Artifact modifiedC = createArtifact( "c", "3.0", Artifact.SCOPE_COMPILE ).artifact;

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, c.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedC} ), res.getArtifacts() );
        Artifact artifact = getArtifact( "c", res.getArtifacts() );
        assertEquals( "Check scope", Artifact.SCOPE_COMPILE, artifact.getScope() );
    }

    public void testResolveRuntimeScopeOverProvidedScope()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0" );
        ArtifactSpec c = createArtifact( "c", "3.0", Artifact.SCOPE_PROVIDED );

        a.addDependency( "c", "2.0", Artifact.SCOPE_RUNTIME );

        Artifact modifiedC = createArtifact( "c", "3.0", Artifact.SCOPE_RUNTIME ).artifact;

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, c.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, modifiedC} ), res.getArtifacts() );
        Artifact artifact = getArtifact( "c", res.getArtifacts() );
        assertEquals( "Check scope", Artifact.SCOPE_RUNTIME, artifact.getScope() );
    }

    public void testProvidedScopeNotTransitive()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0", Artifact.SCOPE_PROVIDED );
        ArtifactSpec b = createArtifact( "b", "1.0" );
        b.addDependency( "c", "3.0", Artifact.SCOPE_PROVIDED );

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, b.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, b.artifact} ), res.getArtifacts() );
    }

    public void testTestScopeNotTransitive()
        throws ArtifactResolutionException
    {
        ArtifactSpec a = createArtifact( "a", "1.0", Artifact.SCOPE_TEST );
        ArtifactSpec b = createArtifact( "b", "1.0" );
        b.addDependency( "c", "3.0", Artifact.SCOPE_TEST );

        ArtifactResolutionResult res = collect( createSet( new Object[]{a.artifact, b.artifact} ) );
        assertEquals( "Check artifact list", createSet( new Object[]{a.artifact, b.artifact} ), res.getArtifacts() );
    }

    private Artifact getArtifact( String id, Set artifacts )
    {
        for ( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();
            if ( a.getArtifactId().equals( id ) && a.getGroupId().equals( GROUP_ID ) )
            {
                return a;
            }
        }
        return null;
    }

    private ArtifactResolutionResult collect( Set artifacts )
        throws ArtifactResolutionException
    {
        return artifactCollector.collect( artifacts, projectArtifact.artifact, null, null, source, null,
                                          artifactFactory );
    }

    private ArtifactResolutionResult collect( ArtifactSpec a )
        throws ArtifactResolutionException
    {
        return artifactCollector.collect( Collections.singleton( a.artifact ), projectArtifact.artifact, null, null,
                                          source, null, artifactFactory );
    }

    private ArtifactResolutionResult collect( ArtifactSpec a, ArtifactFilter filter )
        throws ArtifactResolutionException
    {
        return artifactCollector.collect( Collections.singleton( a.artifact ), projectArtifact.artifact, null, null,
                                          source, filter, artifactFactory );
    }

    private ArtifactResolutionResult collect( ArtifactSpec a, Artifact managedVersion )
        throws ArtifactResolutionException
    {
        Map managedVersions = Collections.singletonMap( managedVersion.getDependencyConflictId(),
                                                        managedVersion.getVersion() );
        return artifactCollector.collect( Collections.singleton( a.artifact ), projectArtifact.artifact,
                                          managedVersions, null, null, source, null, artifactFactory );
    }

    private ArtifactSpec createArtifact( String id, String version )
    {
        return createArtifact( id, version, Artifact.SCOPE_COMPILE );
    }

    private ArtifactSpec createArtifact( String id, String version, String scope )
    {
        ArtifactSpec spec = new ArtifactSpec();
        spec.artifact = artifactFactory.createArtifact( GROUP_ID, id, version, scope, "jar" );
        source.artifacts.put( spec.artifact.getId(), spec );
        return spec;
    }

    private static Set createSet( Object[] x )
    {
        return new HashSet( Arrays.asList( x ) );
    }

    private class ArtifactSpec
    {
        Artifact artifact;

        Set dependencies = new HashSet();

        public ArtifactSpec addDependency( String id, String version )
        {
            return addDependency( id, version, null );
        }

        public ArtifactSpec addDependency( String id, String version, String scope )
        {
            ArtifactSpec dep = createArtifact( id, version, scope );
            dependencies.add( dep.artifact );
            return dep;
        }
    }

    private class Source
        implements ArtifactMetadataSource
    {
        Map artifacts = new HashMap();

        public Set retrieve( Artifact artifact, ArtifactRepository localRepository, List remoteRepositories )
            throws ArtifactMetadataRetrievalException, ArtifactResolutionException
        {
            ArtifactSpec a = (ArtifactSpec) artifacts.get( artifact.getId() );
            return createArtifacts( artifactFactory, a.dependencies, artifact.getScope(),
                                    artifact.getDependencyFilter() );
        }

        private Set createArtifacts( ArtifactFactory artifactFactory, Set dependencies, String inheritedScope,
                                     ArtifactFilter dependencyFilter )
        {
            Set projectArtifacts = new HashSet();

            for ( Iterator i = dependencies.iterator(); i.hasNext(); )
            {
                Artifact d = (Artifact) i.next();

                Artifact artifact = artifactFactory.createArtifact( d.getGroupId(), d.getArtifactId(), d.getVersion(),
                                                                    d.getScope(), d.getType(), inheritedScope );

                if ( artifact != null && ( dependencyFilter == null || dependencyFilter.include( artifact ) ) )
                {
                    artifact.setDependencyFilter( dependencyFilter );

                    projectArtifacts.add( artifact );
                }
            }

            return projectArtifacts;
        }
    }
}
