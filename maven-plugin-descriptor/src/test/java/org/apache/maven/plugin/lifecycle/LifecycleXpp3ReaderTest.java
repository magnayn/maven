package org.apache.maven.plugin.lifecycle;

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

import junit.framework.TestCase;
import org.apache.maven.plugin.lifecycle.io.xpp3.LifecycleMappingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Test the lifecycle reader.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class LifecycleXpp3ReaderTest
    extends TestCase
{
    public void testLifecycleReader()
        throws IOException, XmlPullParserException
    {
        LifecycleMappingsXpp3Reader reader = new LifecycleMappingsXpp3Reader();
        LifecycleConfiguration config = reader.read( new InputStreamReader( getClass().getResourceAsStream( "/lifecycle.xml" ) ) );
        assertEquals( "check number of lifecycles", 1, config.getLifecycles().size() );
        Lifecycle l = (Lifecycle) config.getLifecycles().iterator().next();
        assertEquals( "check id", "clover", l.getId() );
        assertEquals( "check number of phases", 1, l.getPhases().size() );
        Phase p = (Phase) l.getPhases().iterator().next();
        assertEquals( "check id", "generate-sources", p.getId() );
        assertEquals( "check number of executions", 1, p.getExecutions().size() );
        Execution e = (Execution) p.getExecutions().iterator().next();
        assertEquals( "check configuration", "true", ((Xpp3Dom) e.getConfiguration()).getChild( "debug" ).getValue() );
        assertEquals( "check number of goals", 1, e.getGoals().size() );
        String g = (String) e.getGoals().iterator().next();
        assertEquals( "check goal", "clover:compiler", g );
    }
}
