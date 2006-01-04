package org.apache.maven.reporting;

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

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.RendererException;
import org.apache.maven.reporting.sink.MultiPageSink;
import org.apache.maven.reporting.sink.SinkFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: MavenReport.java 163376 2005-02-23 00:06:06Z brett $
 */
public abstract class AbstractMavenMultiPageReport
    extends AbstractMavenReport
{
    private SinkFactory factory;

    private List sinks = new ArrayList();

    public void setSinkFactory( SinkFactory factory )
    {
        this.factory = factory;
    }

    public SinkFactory getSinkFactory()
    {
        return factory;
    }

    public boolean useDefaultSiteDescriptor()
    {
        return true;
    }

    public abstract boolean usePageLinkBar();

    private Sink getSink( String outputName )
        throws RendererException, IOException
    {
        return factory.getSink( outputName );
    }

    public MultiPageSink startPage( String outputName )
        throws RendererException, IOException
    {
        return new MultiPageSink( outputName, getSink( outputName ) );
    }

    public void endPage( MultiPageSink sink )
    {
        if ( usePageLinkBar() )
        {
            sinks.add( sink );
        }
        else
        {
            sink.closeSink();
        }
    }

    protected void closeReport()
    {
        if ( !sinks.isEmpty() )
        {
            for ( Iterator i = sinks.iterator(); i.hasNext(); )
            {
                MultiPageSink currentSink = (MultiPageSink) i.next();

                currentSink.paragraph();

                for ( int counter = 1; counter <= sinks.size(); counter++ )
                {
                    if ( counter > 1 )
                    {
                        currentSink.text( "&nbsp;" );
                    }
                    MultiPageSink sink = (MultiPageSink) sinks.get( counter - 1 );
                    sink.link( sink.getOutputName() + ".html" );
                    sink.text( String.valueOf( counter ) );
                    sink.link_();
                }
                currentSink.paragraph_();
                currentSink.closeSink();
            }
        }

        super.closeReport();
    }
}
