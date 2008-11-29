package org.apache.maven.artifact.resolver.conflict;

import org.apache.maven.artifact.ArtifactScopeEnum;
import org.apache.maven.artifact.resolver.metadata.ArtifactMetadata;
import org.apache.maven.artifact.resolver.metadata.MetadataGraph;
import org.apache.maven.artifact.resolver.metadata.MetadataGraphEdge;
import org.apache.maven.artifact.resolver.metadata.MetadataGraphVertex;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.logging.Logger;

/**
 *
 * @author <a href="mailto:oleg@codehaus.org">Oleg Gusakov</a>
 * 
 * @version $Id$
 */

public class DefaultGraphConflictResolverTest
extends PlexusTestCase
{
	Logger log;
	
	GraphConflictResolver resolver;
	
	MetadataGraph graph;

	MetadataGraphVertex v1;
	MetadataGraphVertex v2;
	MetadataGraphVertex v3;
	MetadataGraphVertex v4;
    //------------------------------------------------------------------------------------------
    @Override
	protected void setUp() throws Exception
	{
		super.setUp();
		resolver = (GraphConflictResolver) lookup( GraphConflictResolver.ROLE, "default" );

    	/*
    	 *       v2
    	 *   v1<
    	 *      v3-v4
    	 * 
    	 */
    	graph = new MetadataGraph( 4, 3 );
    	v1 = graph.addVertex(new ArtifactMetadata("g","a1","1.0"));
    	graph.setEntry(v1);
    	v2 = graph.addVertex(new ArtifactMetadata("g","a2","1.0"));
    	v3 = graph.addVertex(new ArtifactMetadata("g","a3","1.0"));
    	v4 = graph.addVertex(new ArtifactMetadata("g","a4","1.0"));

    	// v1-->v2
    	graph.addEdge(v1, v2, new MetadataGraphEdge( "1.1", true, null, null, 2, 1 ) );
    	graph.addEdge(v1, v2, new MetadataGraphEdge( "1.2", true, null, null, 2, 2 ) );
    	
    	// v1-->v3
    	graph.addEdge(v1, v3, new MetadataGraphEdge( "1.1", true, null, null, 2, 1 ) );
    	graph.addEdge(v1, v3, new MetadataGraphEdge( "1.2", true, null, null, 4, 2 ) );
    	
    	// v3-->v4
    	graph.addEdge(v3, v4, new MetadataGraphEdge( "1.1", true, ArtifactScopeEnum.runtime, null, 2, 1 ) );
    	graph.addEdge(v3, v4, new MetadataGraphEdge( "1.2", true, ArtifactScopeEnum.provided, null, 2, 2 ) );
	}
    //------------------------------------------------------------------------------------------
    public void testCompileResolution()
    throws Exception
    {
    	MetadataGraph res;
    	
    	res = resolver.resolveConflicts( graph, ArtifactScopeEnum.compile );
    	
    	assertNotNull("null graph after resolver", res );
    	assertNotNull("no vertices in the resulting graph after resolver", res.getVertices() );

    	assertNotNull("no edges in the resulting graph after resolver", res.getExcidentEdges(v1) );

    	assertEquals( "wrong # of vertices in the resulting graph after resolver", 4, res.getVertices().size() );
    	assertEquals( "wrong # of excident edges in the resulting graph entry after resolver", 2, res.getExcidentEdges(v1).size() );

    	assertEquals( "wrong # of v2 incident edges in the resulting graph after resolver", 1, res.getIncidentEdges(v2).size() );
    	assertEquals( "wrong edge v1-v2 in the resulting graph after resolver", "1.2", res.getIncidentEdges(v2).get(0).getVersion() );

    	assertEquals( "wrong # of edges v1-v3 in the resulting graph after resolver", 1, res.getIncidentEdges(v3).size() );
    	assertEquals( "wrong edge v1-v3 in the resulting graph after resolver", "1.1", res.getIncidentEdges(v3).get(0).getVersion() );

    	assertEquals( "wrong # of edges v3-v4 in the resulting graph after resolver", 1, res.getIncidentEdges(v4).size() );
    	assertEquals( "wrong edge v3-v4 in the resulting graph after resolver", "1.2", res.getIncidentEdges(v4).get(0).getVersion() );
    }
    //------------------------------------------------------------------------------------------
    public void testRuntimeResolution()
    throws Exception
    {
    	MetadataGraph res;
    	
    	res = resolver.resolveConflicts( graph, ArtifactScopeEnum.runtime );
    	
    	assertNotNull("null graph after resolver", res );
    	assertNotNull("no vertices in the resulting graph after resolver", res.getVertices() );
    	assertNotNull("no edges in the resulting graph after resolver", res.getExcidentEdges(v1) );

    	assertEquals( "wrong # of vertices in the resulting graph after resolver", 4, res.getVertices().size() );
    	assertEquals( "wrong # of excident edges in the resulting graph entry after resolver", 2, res.getExcidentEdges(v1).size() );

    	assertEquals( "wrong # of v2 incident edges in the resulting graph after resolver", 1, res.getIncidentEdges(v2).size() );
    	assertEquals( "wrong edge v1-v2 in the resulting graph after resolver", "1.2", res.getIncidentEdges(v2).get(0).getVersion() );

    	assertEquals( "wrong # of edges v1-v3 in the resulting graph after resolver", 1, res.getIncidentEdges(v3).size() );
    	assertEquals( "wrong edge v1-v3 in the resulting graph after resolver", "1.1", res.getIncidentEdges(v3).get(0).getVersion() );

    	assertEquals( "wrong # of edges v3-v4 in the resulting graph after resolver", 1, res.getIncidentEdges(v4).size() );
    	assertEquals( "wrong edge v3-v4 in the resulting graph after resolver", "1.1", res.getIncidentEdges(v4).get(0).getVersion() );
    }
    //------------------------------------------------------------------------------------------
    public void testTestResolution()
    throws Exception
    {
    	MetadataGraph res;
    	
    	res = resolver.resolveConflicts( graph, ArtifactScopeEnum.test );
    	
    	assertNotNull("null graph after resolver", res );
    	assertNotNull("no vertices in the resulting graph after resolver", res.getVertices() );
    	assertNotNull("no edges in the resulting graph after resolver", res.getExcidentEdges(v1) );

    	assertEquals( "wrong # of vertices in the resulting graph after resolver", 4, res.getVertices().size() );
    	assertEquals( "wrong # of excident edges in the resulting graph entry after resolver", 2, res.getExcidentEdges(v1).size() );

    	assertEquals( "wrong # of v2 incident edges in the resulting graph after resolver", 1, res.getIncidentEdges(v2).size() );
    	assertEquals( "wrong edge v1-v2 in the resulting graph after resolver", "1.2", res.getIncidentEdges(v2).get(0).getVersion() );

    	assertEquals( "wrong # of edges v1-v3 in the resulting graph after resolver", 1, res.getIncidentEdges(v3).size() );
    	assertEquals( "wrong edge v1-v3 in the resulting graph after resolver", "1.1", res.getIncidentEdges(v3).get(0).getVersion() );

    	assertEquals( "wrong # of edges v3-v4 in the resulting graph after resolver", 1, res.getIncidentEdges(v4).size() );
    	assertEquals( "wrong edge v3-v4 in the resulting graph after resolver", "1.2", res.getIncidentEdges(v4).get(0).getVersion() );
    }
    //------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------
}
