package org.apache.maven.cli;

/* ====================================================================
 *   Copyright 2001-2004 The Apache Software Foundation.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * ====================================================================
 */

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.maven.Maven;
import org.apache.maven.MavenConstants;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResponse;
import org.apache.maven.model.Repository;
import org.apache.maven.monitor.event.DefaultEventDispatcher;
import org.apache.maven.monitor.event.DefaultEventMonitor;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.Plugin;
import org.apache.maven.settings.MavenSettings;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Profile;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.embed.ArtifactEnabledEmbedder;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
public class MavenCli
{
    public static final String POMv4 = "pom.xml";

    public static final String userHome = System.getProperty( "user.home" );

    public static File userDir = new File( System.getProperty( "user.dir" ) );

    public static int main( String[] args, ClassWorld classWorld )
        throws Exception
    {
        // ----------------------------------------------------------------------
        // Setup the command line parser
        // ----------------------------------------------------------------------

        CLIManager cliManager = new CLIManager();

        CommandLine commandLine = cliManager.parse( args );

        // ----------------------------------------------------------------------
        //
        // 1) maven user configuration directory ( ~/.m2 )
        // 2) maven home
        // 3) local repository
        //
        // ----------------------------------------------------------------------

        //        File userConfigurationDirectory = getUserConfigurationDirectory();

        //        Properties mavenProperties = getMavenProperties( userConfigurationDirectory );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        initializeSystemProperties( commandLine );

        // ----------------------------------------------------------------------
        // Process particular command line options
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.HELP ) )
        {
            cliManager.displayHelp();
            return 0;
        }
        if ( commandLine.hasOption( CLIManager.VERSION ) )
        {
            System.out.println( "Maven version: " );
            return 0;
        }

        // ----------------------------------------------------------------------
        // We will ultimately not require a flag to indicate the reactor as
        // we should take this from the execution context i.e. what the type
        // is stated as in the POM.
        // ----------------------------------------------------------------------

        MavenExecutionRequest request = null;

        File projectFile = new File( userDir, POMv4 );

        EventDispatcher eventDispatcher = new DefaultEventDispatcher();

        // ----------------------------------------------------------------------
        // Now that we have everything that we need we will fire up plexus and
        // bring the maven component to life for use.
        // ----------------------------------------------------------------------

        ArtifactEnabledEmbedder embedder = new ArtifactEnabledEmbedder();

        embedder.start( classWorld );

        MavenSettingsBuilder settingsBuilder = (MavenSettingsBuilder) embedder.lookup( MavenSettingsBuilder.ROLE );

        MavenSettings settings = settingsBuilder.buildSettings();

        ArtifactRepositoryFactory artifactRepositoryFactory = (ArtifactRepositoryFactory) embedder.lookup(
            ArtifactRepositoryFactory.ROLE );

        if ( commandLine.hasOption( CLIManager.UPDATE_SNAPSHOTS ) )
        {
            artifactRepositoryFactory.setGlobalSnapshotPolicy( ArtifactRepository.SNAPSHOT_POLICY_ALWAYS );
        }

        // TODO: Switch the default repository layout id to "default" when the
        // conversion is done.
        String repoLayoutId = "legacy";

        if ( commandLine.hasOption( CLIManager.VERSION_1_REPO ) )
        {
            repoLayoutId = "legacy";
        }

        if ( commandLine.hasOption( CLIManager.VERSION_2_REPO ) )
        {
            repoLayoutId = "default";
        }

        ArtifactRepositoryLayout repositoryLayout = (ArtifactRepositoryLayout) embedder.lookup(
            ArtifactRepositoryLayout.ROLE, repoLayoutId );

        ArtifactRepository localRepository = getLocalRepository( settings, artifactRepositoryFactory, repositoryLayout );

        if ( commandLine.hasOption( CLIManager.REACTOR ) )
        {
            // TODO: should we now include the pom.xml in the current directory?
            String includes = System.getProperty( "maven.reactor.includes", "**/" + POMv4 );

            String excludes = System.getProperty( "maven.reactor.excludes", POMv4 );

            request = new DefaultMavenExecutionRequest( localRepository, settings, eventDispatcher,
                                                        commandLine.getArgList(), FileUtils.getFiles( userDir,
                                                                                                      includes,
                                                                                                      excludes ),
                                                        userDir.getPath() );
        }
        else
        {
            List files = Collections.EMPTY_LIST;
            if ( projectFile.exists() )
            {
                files = Collections.singletonList( projectFile );
            }
            request = new DefaultMavenExecutionRequest( localRepository, settings, eventDispatcher,
                                                        commandLine.getArgList(), files, userDir.getPath() );

            if ( commandLine.hasOption( CLIManager.NON_RECURSIVE ) )
            {
                request.setRecursive( false );
            }
        }

        LoggerManager manager = (LoggerManager) embedder.lookup( LoggerManager.ROLE );
        if ( commandLine.hasOption( CLIManager.DEBUG ) )
        {
            manager.setThreshold( Logger.LEVEL_DEBUG );
        }

        // TODO [BP]: do we set one per mojo? where to do it?
        Logger logger = manager.getLoggerForComponent( Plugin.ROLE );
        if ( logger != null )
        {
            request.setLog( new DefaultLog( logger ) );

            request.addEventMonitor( new DefaultEventMonitor( logger ) );
        }

        // TODO [BP]: doing this here as it is CLI specific, though it doesn't feel like the right place (likewise logger).
        WagonManager wagonManager = (WagonManager) embedder.lookup( WagonManager.ROLE );
        wagonManager.setDownloadMonitor( new ConsoleDownloadMonitor() );

        Maven maven = (Maven) embedder.lookup( Maven.ROLE );

        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        MavenExecutionResponse response = maven.execute( request );

        if ( response != null && response.isExecutionFailure() )
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

    // ----------------------------------------------------------------------
    // System properties handling
    // ----------------------------------------------------------------------

    private static void initializeSystemProperties( CommandLine commandLine )
    {
        // ----------------------------------------------------------------------
        // Options that are set on the command line become system properties
        // and therefore are set in the session properties. System properties
        // are most dominant.
        // ----------------------------------------------------------------------

        if ( commandLine.hasOption( CLIManager.SET_SYSTEM_PROPERTY ) )
        {
            String[] defStrs = commandLine.getOptionValues( CLIManager.SET_SYSTEM_PROPERTY );
            for ( int i = 0; i < defStrs.length; ++i )
            {
                setCliProperty( defStrs[i] );
            }
        }
    }

    private static void setCliProperty( String property )
    {
        String name = null;

        String value = null;

        int i = property.indexOf( "=" );

        if ( i <= 0 )
        {
            name = property.trim();

            value = "true";
        }
        else
        {
            name = property.substring( 0, i ).trim();

            value = property.substring( i + 1 ).trim();
        }

        System.setProperty( name, value );
    }

    // ----------------------------------------------------------------------
    // Command line manager
    // ----------------------------------------------------------------------

    static class CLIManager
    {
        public static final char NO_BANNER = 'b';

        public static final char SET_SYSTEM_PROPERTY = 'D';

        public static final char WORK_OFFLINE = 'o';

        public static final char REACTOR = 'r';

        public static final char DEBUG = 'X';

        public static final char HELP = 'h';

        public static final char VERSION = 'v';

        public static final char LIST_GOALS = 'g';

        private Options options = null;

        public static final char NON_RECURSIVE = 'N';

        public static final char VERSION_1_REPO = 'A';

        // TODO: this is a hack until we can get the main repo converted...
        public static final char VERSION_2_REPO = 'a';

        public static final char UPDATE_SNAPSHOTS = 'U';

        public CLIManager()
        {
            options = new Options();
            options.addOption( OptionBuilder.withLongOpt( "nobanner" ).withDescription( "Suppress logo banner" ).create(
                NO_BANNER ) );
            options.addOption( OptionBuilder.withLongOpt( "define" ).hasArg().withDescription(
                "Define a system property" ).create( SET_SYSTEM_PROPERTY ) );
            options.addOption( OptionBuilder.withLongOpt( "offline" ).hasArg().withDescription( "Work offline" ).create(
                WORK_OFFLINE ) );
            options.addOption( OptionBuilder.withLongOpt( "mojoDescriptors" ).withDescription(
                "Display available mojoDescriptors" ).create( LIST_GOALS ) );
            options.addOption( OptionBuilder.withLongOpt( "help" ).withDescription( "Display help information" ).create(
                HELP ) );
            options.addOption( OptionBuilder.withLongOpt( "offline" ).withDescription( "Build is happening offline" ).create(
                WORK_OFFLINE ) );
            options.addOption( OptionBuilder.withLongOpt( "version" ).withDescription( "Display version information" ).create(
                VERSION ) );
            options.addOption( OptionBuilder.withLongOpt( "debug" ).withDescription( "Produce execution debug output" ).create(
                DEBUG ) );
            options.addOption( OptionBuilder.withLongOpt( "reactor" ).withDescription(
                "Execute goals for project found in the reactor" ).create( REACTOR ) );
            options.addOption( OptionBuilder.withLongOpt( "non-recursive" ).withDescription(
                "Do not recurse into sub-projects" ).create( NON_RECURSIVE ) );
            options.addOption( OptionBuilder.withLongOpt( "v1-local-repository" ).withDescription(
                "Use legacy layout for local artifact repository" ).create( VERSION_1_REPO ) );

            options.addOption( OptionBuilder.withLongOpt( "v2-local-repository" ).withDescription(
                "Use new layout for local artifact repository" ).create( VERSION_2_REPO ) );
            options.addOption( OptionBuilder.withLongOpt( "update-snapshots" ).withDescription(
                "Update all snapshots regardless of repository policies" ).create( UPDATE_SNAPSHOTS ) );
        }

        public CommandLine parse( String[] args )
            throws ParseException
        {
            CommandLineParser parser = new PosixParser();
            return parser.parse( options, args );
        }

        public void displayHelp()
        {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "maven [options] [goal [goal2 [goal3] ...]]", "\nOptions:", options, "\n" );
        }
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    protected static File getUserConfigurationDirectory()
    {
        File mavenUserConfigurationDirectory = new File( userHome, MavenConstants.MAVEN_USER_CONFIGURATION_DIRECTORY );
        if ( !mavenUserConfigurationDirectory.exists() )
        {
            if ( !mavenUserConfigurationDirectory.mkdirs() )
            {
                //throw a configuration exception
            }
        }
        return mavenUserConfigurationDirectory;
    }

    protected static Properties getMavenProperties( File mavenHomeLocal )
    {
        Properties mavenProperties = new Properties();
        File mavenPropertiesFile = new File( mavenHomeLocal, MavenConstants.MAVEN_PROPERTIES );
        try
        {
            mavenProperties.load( new FileInputStream( mavenPropertiesFile ) );
        }
        catch ( IOException e )
        {
            // do nothing
        }
        return mavenProperties;
    }

    protected static ArtifactRepository getLocalRepository( MavenSettings settings,
                                                            ArtifactRepositoryFactory repoFactory,
                                                            ArtifactRepositoryLayout repositoryLayout )
        throws Exception
    {
        Profile profile = settings.getActiveProfile();

        String localRepository = null;
        if ( profile != null )
        {
            localRepository = profile.getLocalRepository();
        }

        if ( localRepository == null )
        {
            String userConfigurationDirectory = System.getProperty( "user.home" ) + "/.m2";
            localRepository =
                new File( userConfigurationDirectory, MavenConstants.MAVEN_REPOSITORY ).getAbsolutePath();
        }

        // TODO [BP]: this should not be necessary - grep for and remove
        System.setProperty( MavenConstants.MAVEN_REPO_LOCAL, localRepository );

        Repository repo = new Repository();

        repo.setId( "local" );

        repo.setUrl( "file://" + localRepository );

        return repoFactory.createArtifactRepository( repo, settings, repositoryLayout );
    }
}