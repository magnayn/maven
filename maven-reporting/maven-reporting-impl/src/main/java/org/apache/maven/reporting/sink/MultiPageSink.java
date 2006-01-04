package org.apache.maven.reporting.sink;

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

import org.apache.maven.doxia.sink.SinkAdapter;
import org.apache.maven.doxia.sink.Sink;

/**
 * @author Emmanuel Venisse
 *
 */
public class MultiPageSink
    extends SinkAdapter
{
    private String outputName;

    private Sink sink;

    public MultiPageSink( String outputName, Sink sink )
    {
        this.outputName = outputName;
        this.sink = sink;
    }

    public String getOutputName()
    {
        return outputName;
    }

    public Sink getEmbeddedSink()
    {
        return sink;
    }

    public void closeSink()
    {
        sink.body_();
        sink.flush();
        sink.close();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#anchor_()
     */
    public void anchor_()
    {
        sink.anchor_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#anchor(java.lang.String)
     */
    public void anchor( String arg0 )
    {
        sink.anchor( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#author_()
     */
    public void author_()
    {
        sink.author_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#author()
     */
    public void author()
    {
        sink.author();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#body()
     */
    public void body()
    {
        sink.body();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#body_()
     */
    public void body_()
    {
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#bold_()
     */
    public void bold_()
    {
        sink.bold_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#bold()
     */
    public void bold()
    {
        sink.bold();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#close()
     */
    public void close()
    {
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#date_()
     */
    public void date_()
    {
        sink.date_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#date()
     */
    public void date()
    {
        sink.date();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definedTerm_()
     */
    public void definedTerm_()
    {
        sink.definedTerm_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definedTerm()
     */
    public void definedTerm()
    {
        sink.definedTerm();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definition_()
     */
    public void definition_()
    {
        sink.definition_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definition()
     */
    public void definition()
    {
        sink.definition();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definitionList_()
     */
    public void definitionList_()
    {
        sink.definitionList_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definitionList()
     */
    public void definitionList()
    {
        sink.definitionList();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definitionListItem_()
     */
    public void definitionListItem_()
    {
        sink.definitionListItem_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#definitionListItem()
     */
    public void definitionListItem()
    {
        sink.definitionListItem();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#figure_()
     */
    public void figure_()
    {
        sink.figure_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#figure()
     */
    public void figure()
    {
        sink.figure();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#figureCaption_()
     */
    public void figureCaption_()
    {
        sink.figureCaption_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#figureCaption()
     */
    public void figureCaption()
    {
        sink.figureCaption();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#figureGraphics(java.lang.String)
     */
    public void figureGraphics( String arg0 )
    {
        sink.figureGraphics( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#flush()
     */
    public void flush()
    {
        sink.flush();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#head_()
     */
    public void head_()
    {
        sink.head_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#head()
     */
    public void head()
    {
        sink.head();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#horizontalRule()
     */
    public void horizontalRule()
    {
        sink.horizontalRule();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#italic_()
     */
    public void italic_()
    {
        sink.italic_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#italic()
     */
    public void italic()
    {
        sink.italic();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#lineBreak()
     */
    public void lineBreak()
    {
        sink.lineBreak();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#link_()
     */
    public void link_()
    {
        sink.link_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#link(java.lang.String)
     */
    public void link( String arg0 )
    {
        sink.link( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#list_()
     */
    public void list_()
    {
        sink.list_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#list()
     */
    public void list()
    {
        sink.list();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#listItem_()
     */
    public void listItem_()
    {
        sink.listItem_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#listItem()
     */
    public void listItem()
    {
        sink.listItem();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#monospaced_()
     */
    public void monospaced_()
    {
        sink.monospaced_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#monospaced()
     */
    public void monospaced()
    {
        sink.monospaced();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#nonBreakingSpace()
     */
    public void nonBreakingSpace()
    {
        sink.nonBreakingSpace();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#numberedList_()
     */
    public void numberedList_()
    {
        sink.numberedList_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#numberedList(int)
     */
    public void numberedList( int arg0 )
    {
        sink.numberedList( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#numberedListItem_()
     */
    public void numberedListItem_()
    {
        sink.numberedListItem_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#numberedListItem()
     */
    public void numberedListItem()
    {
        sink.numberedListItem();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#pageBreak()
     */
    public void pageBreak()
    {
        sink.pageBreak();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#paragraph_()
     */
    public void paragraph_()
    {
        sink.paragraph_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#paragraph()
     */
    public void paragraph()
    {
        sink.paragraph();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#rawText(java.lang.String)
     */
    public void rawText( String arg0 )
    {
        sink.rawText( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section1_()
     */
    public void section1_()
    {
        sink.section1_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section1()
     */
    public void section1()
    {
        sink.section1();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section2_()
     */
    public void section2_()
    {
        sink.section2_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section2()
     */
    public void section2()
    {
        sink.section2();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section3_()
     */
    public void section3_()
    {
        sink.section3_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section3()
     */
    public void section3()
    {
        sink.section3();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section4_()
     */
    public void section4_()
    {
        sink.section4_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section4()
     */
    public void section4()
    {
        sink.section4();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section5_()
     */
    public void section5_()
    {
        sink.section5_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#section5()
     */
    public void section5()
    {
        sink.section5();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle_()
     */
    public void sectionTitle_()
    {
        sink.sectionTitle_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle()
     */
    public void sectionTitle()
    {
        sink.sectionTitle();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle1_()
     */
    public void sectionTitle1_()
    {
        sink.sectionTitle1_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle1()
     */
    public void sectionTitle1()
    {
        sink.sectionTitle1();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle2_()
     */
    public void sectionTitle2_()
    {
        sink.sectionTitle2_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle2()
     */
    public void sectionTitle2()
    {
        sink.sectionTitle2();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle3_()
     */
    public void sectionTitle3_()
    {
        sink.sectionTitle3_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle3()
     */
    public void sectionTitle3()
    {
        sink.sectionTitle3();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle4_()
     */
    public void sectionTitle4_()
    {
        sink.sectionTitle4_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle4()
     */
    public void sectionTitle4()
    {
        sink.sectionTitle4();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle5_()
     */
    public void sectionTitle5_()
    {
        sink.sectionTitle5_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#sectionTitle5()
     */
    public void sectionTitle5()
    {
        sink.sectionTitle5();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#table_()
     */
    public void table_()
    {
        sink.table_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#table()
     */
    public void table()
    {
        sink.table();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableCaption_()
     */
    public void tableCaption_()
    {
        sink.tableCaption_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableCaption()
     */
    public void tableCaption()
    {
        sink.tableCaption();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableCell_()
     */
    public void tableCell_()
    {
        sink.tableCell_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableCell()
     */
    public void tableCell()
    {
        sink.tableCell();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableHeaderCell_()
     */
    public void tableHeaderCell_()
    {
        sink.tableHeaderCell_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableHeaderCell()
     */
    public void tableHeaderCell()
    {
        sink.tableHeaderCell();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableRow_()
     */
    public void tableRow_()
    {
        sink.tableRow_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableRow()
     */
    public void tableRow()
    {
        sink.tableRow();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableRows_()
     */
    public void tableRows_()
    {
        sink.tableRows_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#tableRows(int[], boolean)
     */
    public void tableRows( int[] arg0, boolean arg1 )
    {
        sink.tableRows( arg0, arg1 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#text(java.lang.String)
     */
    public void text( String arg0 )
    {
        sink.text( arg0 );
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#title_()
     */
    public void title_()
    {
        sink.title_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#title()
     */
    public void title()
    {
        sink.title();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#verbatim_()
     */
    public void verbatim_()
    {
        sink.verbatim_();
    }

    /**
     * @see org.codehaus.doxia.module.xhtml.SinkAdapter#verbatim(boolean)
     */
    public void verbatim( boolean arg0 )
    {
        sink.verbatim( arg0 );
    }
}
