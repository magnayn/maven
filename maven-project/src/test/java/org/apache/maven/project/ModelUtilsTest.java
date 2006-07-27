package org.apache.maven.project;

import junit.framework.TestCase;

import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

public class ModelUtilsTest
    extends TestCase
{
    
    public void testShouldNotInheritPluginWithInheritanceSetToFalse()
    {
        PluginContainer parent = new PluginContainer();
        
        Plugin parentPlugin = createPlugin( "group", "artifact", "1.0", Collections.EMPTY_MAP );
        parentPlugin.setInherited( "false" );
        
        parent.addPlugin( parentPlugin );
        
        PluginContainer child = new PluginContainer();
        
        child.addPlugin( createPlugin( "group3", "artifact3", "1.0", Collections.EMPTY_MAP ) );
        
        ModelUtils.mergePluginLists( child, parent, true );
        
        List results = child.getPlugins();
        
        assertEquals( 1, results.size() );
        
        Plugin result1 = (Plugin) results.get( 0 );
        assertEquals( "group3", result1.getGroupId() );
        assertEquals( "artifact3", result1.getArtifactId() );
    }
    
    /**
     * Test that this is the resulting ordering of plugins after merging:
     * 
     * Given:
     * 
     *   parent: X -> A -> B -> D -> E
     *   child: Y -> A -> C -> D -> F
     *  
     * Result: 
     * 
     *   X -> Y -> A -> B -> C -> D -> E -> F
     */
    public void testShouldPreserveChildOrderingOfPluginsAfterParentMerge()
    {
        PluginContainer parent = new PluginContainer();
        
        parent.addPlugin( createPlugin( "group", "artifact", "1.0", Collections.EMPTY_MAP ) );
        parent.addPlugin( createPlugin( "group2", "artifact2", "1.0", Collections.singletonMap( "key", "value" ) ) );
        
        PluginContainer child = new PluginContainer();
        
        child.addPlugin( createPlugin( "group3", "artifact3", "1.0", Collections.EMPTY_MAP ) );
        child.addPlugin( createPlugin( "group2", "artifact2", "1.0", Collections.singletonMap( "key2", "value2" ) ) );
        
        ModelUtils.mergePluginLists( child, parent, true );
        
        List results = child.getPlugins();
        
        assertEquals( 3, results.size() );
        
        Plugin result1 = (Plugin) results.get( 0 );
        
        assertEquals( "group", result1.getGroupId() );
        assertEquals( "artifact", result1.getArtifactId() );
        
        Plugin result2 = (Plugin) results.get( 1 );
        
        assertEquals( "group3", result2.getGroupId() );
        assertEquals( "artifact3", result2.getArtifactId() );
        
        Plugin result3 = (Plugin) results.get( 2 );
        
        assertEquals( "group2", result3.getGroupId() );
        assertEquals( "artifact2", result3.getArtifactId() );
        
        Xpp3Dom result3Config = (Xpp3Dom) result3.getConfiguration();
        
        assertNotNull( result3Config );
        
        assertEquals( "value", result3Config.getChild( "key" ).getValue() );
        assertEquals( "value2", result3Config.getChild( "key2" ).getValue() );
    }
    
    private Plugin createPlugin( String groupId, String artifactId, String version, Map configuration )
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId( groupId );
        plugin.setArtifactId( artifactId );
        plugin.setVersion( version );
        
        Xpp3Dom config = new Xpp3Dom( "configuration" );
        
        if( configuration != null )
        {
            for ( Iterator it = configuration.entrySet().iterator(); it.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) it.next();
                
                Xpp3Dom param = new Xpp3Dom( String.valueOf( entry.getKey() ) );
                param.setValue( String.valueOf( entry.getValue() ) );
                
                config.addChild( param );
            }
        }
        
        plugin.setConfiguration( config );
        
        return plugin;
    }

    public void testShouldInheritOnePluginWithExecution()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );

        PluginExecution parentExecution = new PluginExecution();
        parentExecution.setId( "testExecution" );

        parent.addExecution( parentExecution );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );

        ModelUtils.mergePluginDefinitions( child, parent, false );

        assertEquals( 1, child.getExecutions().size() );
    }

    public void testShouldMergeInheritedPluginHavingExecutionWithLocalPlugin()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );

        PluginExecution parentExecution = new PluginExecution();
        parentExecution.setId( "testExecution" );

        parent.addExecution( parentExecution );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );

        PluginExecution childExecution = new PluginExecution();
        childExecution.setId( "testExecution2" );

        child.addExecution( childExecution );

        ModelUtils.mergePluginDefinitions( child, parent, false );

        assertEquals( 2, child.getExecutions().size() );
    }

    public void testShouldMergeOnePluginWithInheritExecutionWithoutDuplicatingPluginInList()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );

        PluginExecution parentExecution = new PluginExecution();
        parentExecution.setId( "testExecution" );

        parent.addExecution( parentExecution );
        
        Build parentContainer = new Build();
        parentContainer.addPlugin( parent );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );
        
        Build childContainer = new Build();
        childContainer.addPlugin( child );

        ModelUtils.mergePluginLists( childContainer, parentContainer, true );
        
        List plugins = childContainer.getPlugins();
        
        assertEquals( 1, plugins.size() );
        
        Plugin plugin = (Plugin) plugins.get( 0 );
        
        assertEquals( 1, plugin.getExecutions().size() );
    }

    public void testShouldMergePluginWithDifferentExecutionFromParentWithoutDuplicatingPluginInList()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );

        PluginExecution parentExecution = new PluginExecution();
        parentExecution.setId( "testExecution" );

        parent.addExecution( parentExecution );
        
        Build parentContainer = new Build();
        parentContainer.addPlugin( parent );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );

        PluginExecution childExecution = new PluginExecution();
        childExecution.setId( "testExecution2" );

        child.addExecution( childExecution );

        
        Build childContainer = new Build();
        childContainer.addPlugin( child );

        ModelUtils.mergePluginLists( childContainer, parentContainer, true );
        
        List plugins = childContainer.getPlugins();
        
        assertEquals( 1, plugins.size() );
        
        Plugin plugin = (Plugin) plugins.get( 0 );
        
        assertEquals( 2, plugin.getExecutions().size() );
    }

    public void testShouldNOTMergeInheritedPluginHavingInheritEqualFalse()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );
        parent.setInherited( "false" );

        PluginExecution parentExecution = new PluginExecution();
        parentExecution.setId( "testExecution" );

        parent.addExecution( parentExecution );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );

        ModelUtils.mergePluginDefinitions( child, parent, true );

        assertEquals( 0, child.getExecutions().size() );
    }

    /**
     * Verifies MNG-1499: The order of the merged list should be the plugins specified by the parent followed by the
     * child list.
     */
    public void testShouldKeepOriginalPluginOrdering()
    {
        Plugin parentPlugin1 = new Plugin();
        parentPlugin1.setArtifactId( "testArtifact" );
        parentPlugin1.setGroupId( "zzz" );  // This will put this plugin last in the sorted map
        parentPlugin1.setVersion( "1.0" );

        PluginExecution parentExecution1 = new PluginExecution();
        parentExecution1.setId( "testExecution" );

        parentPlugin1.addExecution( parentExecution1 );

        Plugin parentPlugin2 = new Plugin();
        parentPlugin2.setArtifactId( "testArtifact" );
        parentPlugin2.setGroupId( "yyy" );
        parentPlugin2.setVersion( "1.0" );

        PluginExecution parentExecution2 = new PluginExecution();
        parentExecution2.setId( "testExecution" );

        parentPlugin2.addExecution( parentExecution2 );

        PluginContainer parentContainer = new PluginContainer();
        parentContainer.addPlugin(parentPlugin1);
        parentContainer.addPlugin(parentPlugin2);


        Plugin childPlugin1 = new Plugin();
        childPlugin1.setArtifactId( "testArtifact" );
        childPlugin1.setGroupId( "bbb" );
        childPlugin1.setVersion( "1.0" );

        PluginExecution childExecution1 = new PluginExecution();
        childExecution1.setId( "testExecution" );

        childPlugin1.addExecution( childExecution1 );

        Plugin childPlugin2 = new Plugin();
        childPlugin2.setArtifactId( "testArtifact" );
        childPlugin2.setGroupId( "aaa" );
        childPlugin2.setVersion( "1.0" );

        PluginExecution childExecution2 = new PluginExecution();
        childExecution2.setId( "testExecution" );

        childPlugin2.addExecution( childExecution2 );

        PluginContainer childContainer = new PluginContainer();
        childContainer.addPlugin(childPlugin1);
        childContainer.addPlugin(childPlugin2);


        ModelUtils.mergePluginLists(childContainer, parentContainer, true);

        assertEquals( 4, childContainer.getPlugins().size() );
        assertSame(parentPlugin1, childContainer.getPlugins().get(0));
        assertSame(parentPlugin2, childContainer.getPlugins().get(1));
        assertSame(childPlugin1, childContainer.getPlugins().get(2));
        assertSame(childPlugin2, childContainer.getPlugins().get(3));
    }

    /**
     * Verifies MNG-1499: The ordering of plugin executions should also be in the specified order.
     */
    public void testShouldKeepOriginalPluginExecutionOrdering()
    {
        Plugin parent = new Plugin();
        parent.setArtifactId( "testArtifact" );
        parent.setGroupId( "testGroup" );
        parent.setVersion( "1.0" );

        PluginExecution parentExecution1 = new PluginExecution();
        parentExecution1.setId( "zzz" );  // Will show up last in the sorted map
        PluginExecution parentExecution2 = new PluginExecution();
        parentExecution2.setId( "yyy" );  // Will show up last in the sorted map

        parent.addExecution( parentExecution1 );
        parent.addExecution( parentExecution2 );

        // this block verifies MNG-1703
        Dependency dep = new Dependency();
        dep.setGroupId( "depGroupId" );
        dep.setArtifactId( "depArtifactId" );
        dep.setVersion( "depVersion" );
        parent.setDependencies( Collections.singletonList( dep ) );

        Plugin child = new Plugin();
        child.setArtifactId( "testArtifact" );
        child.setGroupId( "testGroup" );
        child.setVersion( "1.0" );

        PluginExecution childExecution1 = new PluginExecution();
        childExecution1.setId( "bbb" );
        PluginExecution childExecution2 = new PluginExecution();
        childExecution2.setId( "aaa" );

        child.addExecution( childExecution1 );
        child.addExecution( childExecution2 );

        ModelUtils.mergePluginDefinitions( child, parent, false );

        assertEquals( 4, child.getExecutions().size() );
        assertSame(parentExecution1, child.getExecutions().get(0));
        assertSame(parentExecution2, child.getExecutions().get(1));
        assertSame(childExecution1, child.getExecutions().get(2));
        assertSame(childExecution2, child.getExecutions().get(3));

        // this block prevents MNG-1703
        assertEquals( 1, child.getDependencies().size() );
        Dependency dep2 = (Dependency) child.getDependencies().get( 0 );
        assertEquals( dep.getManagementKey(), dep2.getManagementKey() );
    }
}
