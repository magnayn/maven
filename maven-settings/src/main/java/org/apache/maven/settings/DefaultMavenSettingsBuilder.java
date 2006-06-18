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

import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.util.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

/**
 * @author jdcasey
 * @version $Id: DefaultMavenSettingsBuilder.java 189510 2005-06-08 03:27:43Z jdcasey $
 */
public class DefaultMavenSettingsBuilder
    extends AbstractLogEnabled
    implements MavenSettingsBuilder
{
    // ----------------------------------------------------------------------
    // MavenProfilesBuilder Implementation
    // ----------------------------------------------------------------------

    public Settings buildSettings( File userSettingsFile, File globalSettingsFile )
        throws IOException, XmlPullParserException
    {
        Settings globalSettings = readSettings( globalSettingsFile );

        Settings userSettings = readSettings( userSettingsFile );

        if ( globalSettings == null )
        {
            globalSettings = new Settings();
        }

        if ( userSettings == null )
        {
            userSettings = new Settings();

            userSettings.setRuntimeInfo( new RuntimeInfo( userSettings ) );
        }

        SettingsUtils.merge( userSettings, globalSettings, TrackableBase.GLOBAL_LEVEL );

        activateDefaultProfiles( userSettings );

        return userSettings;
    }

    private Settings readSettings( File settingsFile )
        throws IOException, XmlPullParserException
    {
        if ( settingsFile == null )
        {
            return null;
        }

        Settings settings = null;

        if ( settingsFile.exists() && settingsFile.isFile() )
        {
            FileReader reader = null;
            try
            {
                reader = new FileReader( settingsFile );
                StringWriter sWriter = new StringWriter();

                IOUtil.copy( reader, sWriter );

                String rawInput = sWriter.toString();

                try
                {
                    RegexBasedInterpolator interpolator = new RegexBasedInterpolator();
                    interpolator.addValueSource( new EnvarBasedValueSource() );

                    rawInput = interpolator.interpolate( rawInput, "settings" );
                }
                catch ( Exception e )
                {
                    getLogger().warn(
                        "Failed to initialize environment variable resolver. Skipping environment substitution in settings." );
                    getLogger().debug( "Failed to initialize envar resolver. Skipping resolution.", e );
                }

                StringReader sReader = new StringReader( rawInput );

                SettingsXpp3Reader modelReader = new SettingsXpp3Reader();

                settings = modelReader.read( sReader );

                RuntimeInfo rtInfo = new RuntimeInfo( settings );

                rtInfo.setFile( settingsFile );

                settings.setRuntimeInfo( rtInfo );
            }
            finally
            {
                IOUtil.close( reader );
            }
        }

        return settings;
    }

    private void activateDefaultProfiles( Settings settings )
    {
        List activeProfiles = settings.getActiveProfiles();

        for ( Iterator profiles = settings.getProfiles().iterator(); profiles.hasNext(); )
        {
            Profile profile = (Profile) profiles.next();
            if ( profile.getActivation() != null && profile.getActivation().isActiveByDefault() )
            {
                if ( !activeProfiles.contains( profile.getId() ) )
                {
                    settings.addActiveProfile( profile.getId() );
                }
            }
        }
    }

    private File getFile( String pathPattern, String basedirSysProp, String altLocationSysProp )
    {
        // -------------------------------------------------------------------------------------
        // Alright, here's the justification for all the regexp wizardry below...
        //
        // Continuum and other server-like apps may need to locate the user-level and 
        // global-level settings somewhere other than ${user.home} and ${maven.home},
        // respectively. Using a simple replacement of these patterns will allow them
        // to specify the absolute path to these files in a customized components.xml
        // file. Ideally, we'd do full pattern-evaluation against the sysprops, but this
        // is a first step. There are several replacements below, in order to normalize
        // the path character before we operate on the string as a regex input, and 
        // in order to avoid surprises with the File construction...
        // -------------------------------------------------------------------------------------

        String path = System.getProperty( altLocationSysProp );

        if ( StringUtils.isEmpty( path ) )
        {
            // TODO: This replacing shouldn't be necessary as user.home should be in the
            // context of the container and thus the value would be interpolated by Plexus
            String basedir = System.getProperty( basedirSysProp );
            if ( basedir == null )
            {
                basedir = System.getProperty( "user.dir" );
            }

            basedir = basedir.replaceAll( "\\\\", "/" );
            basedir = basedir.replaceAll( "\\$", "\\\\\\$" );

            path = pathPattern.replaceAll( "\\$\\{" + basedirSysProp + "\\}", basedir );
            path = path.replaceAll( "\\\\", "/" );
            path = path.replaceAll( "//", "/" );

            return new File( path ).getAbsoluteFile();
        }
        else
        {
            return new File( path ).getAbsoluteFile();
        }
    }
}
