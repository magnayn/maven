package org.apache.maven.settings;

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

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;

/**
 * @author jdcasey
 * @version $Id$
 */
public interface MavenSettingsBuilder
{
    String ROLE = MavenSettingsBuilder.class.getName();

    File DEFAULT_USER_SETTINGS_FILE = new File( System.getProperty( "user.home" ), ".m2/settings.xml" );

    File DEFAULT_GLOBAL_SETTINGS_FILE = new File( System
        .getProperty( "maven.home", System.getProperty( "user.dir", "" ) ), "conf/settings.xml" );

    /**
     * @deprecated
     */
    Settings buildSettings()
        throws IOException, XmlPullParserException;

    /**
     * @deprecated
     */
    Settings buildSettings( File userSettingsFile )
        throws IOException, XmlPullParserException;

    /**
     * @since 2.1
     */
    Settings buildSettings( File userSettingsFile, File globalSettingsFile )
        throws IOException, XmlPullParserException;

    /**
     * @since 2.1
     */
    Settings buildSettings( File userSettingsPath, File globalSettingsPath, SettingsBuilderAdvice advice )
        throws IOException, XmlPullParserException;
    
}
