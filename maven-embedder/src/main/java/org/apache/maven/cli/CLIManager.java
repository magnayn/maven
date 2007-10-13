package org.apache.maven.cli;

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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jason van Zyl
 * @version $Revision: 381114 $
 */
public class CLIManager
{
    public static final char ALTERNATE_POM_FILE = 'f';

    public static final char BATCH_MODE = 'B';

    public static final char SET_SYSTEM_PROPERTY = 'D';

    public static final char OFFLINE = 'o';

    public static final char REACTOR = 'r';

    public static final char QUIET = 'q';

    public static final char DEBUG = 'X';

    public static final char ERRORS = 'e';

    public static final char HELP = 'h';

    public static final char VERSION = 'v';

    public static final char NON_RECURSIVE = 'N';

    public static final char UPDATE_SNAPSHOTS = 'U';

    public static final char ACTIVATE_PROFILES = 'P';

    public static final String FORCE_PLUGIN_UPDATES = "cpu";

    public static final String FORCE_PLUGIN_UPDATES2 = "up";

    public static final String SUPPRESS_PLUGIN_UPDATES = "npu";

    public static final String SUPPRESS_PLUGIN_REGISTRY = "npr";

    public static final String SUPRESS_SNAPSHOT_UPDATES = "nsu";

    public static final char CHECKSUM_FAILURE_POLICY = 'C';

    public static final char CHECKSUM_WARNING_POLICY = 'c';

    public static final char ALTERNATE_USER_SETTINGS = 's';

    public static final String FAIL_FAST = "ff";

    public static final String FAIL_AT_END = "fae";

    public static final String FAIL_NEVER = "fn";

    public static final String LOG_FILE = "l";

    private Options options;

    public CLIManager()
    {
        options = new Options();

        options.addOption( OptionBuilder.hasArg( true ).create( ALTERNATE_POM_FILE ) );

        options.addOption(
            OptionBuilder.hasArg( true ).create(
                SET_SYSTEM_PROPERTY ) );
        options.addOption(
            OptionBuilder.create( OFFLINE ) );
        options.addOption(
            OptionBuilder.create( HELP ) );
        options.addOption(
            OptionBuilder.create(
                VERSION ) );
        options.addOption(
            OptionBuilder.create(
                QUIET ) );
        options.addOption(
            OptionBuilder.create(
                DEBUG ) );
        options.addOption(
            OptionBuilder.create(
                ERRORS ) );
        options.addOption( OptionBuilder.create( REACTOR ) );
        options.addOption( OptionBuilder.create( NON_RECURSIVE ) );
        options.addOption( OptionBuilder.create( UPDATE_SNAPSHOTS ) );
        options.addOption( OptionBuilder.hasArg( true ).create( ACTIVATE_PROFILES ) );

        options.addOption( OptionBuilder.create( BATCH_MODE ) );

        options.addOption( OptionBuilder.create( FORCE_PLUGIN_UPDATES ) );
        options.addOption( OptionBuilder.create( FORCE_PLUGIN_UPDATES2 ) );
        options.addOption( OptionBuilder.create( SUPPRESS_PLUGIN_UPDATES ) );

        options.addOption(OptionBuilder
                .create(SUPRESS_SNAPSHOT_UPDATES));

        options.addOption( OptionBuilder.create( SUPPRESS_PLUGIN_REGISTRY ) );

        options.addOption( OptionBuilder.create( CHECKSUM_FAILURE_POLICY ) );
        options.addOption(
            OptionBuilder.create(
                CHECKSUM_WARNING_POLICY ) );

        options.addOption( OptionBuilder.hasArg( true )
            .create( ALTERNATE_USER_SETTINGS ) );

        options.addOption( OptionBuilder.create( FAIL_FAST ) );

        options.addOption( OptionBuilder.withLongOpt( "fail-at-end" ).withDescription(
            "Only fail the build afterwards; allow all non-impacted builds to continue" ).create( FAIL_AT_END ) );

        options.addOption( OptionBuilder.withLongOpt( "fail-never" ).withDescription(
            "NEVER fail the build, regardless of project result" ).create( FAIL_NEVER ) );

        options.addOption( OptionBuilder.withLongOpt( "log-file" ).hasArg().withDescription(
            "Log file to where all build output will go." ).create( LOG_FILE ) );
    }

    public CommandLine parse( String[] args )
        throws ParseException
    {
        // We need to eat any quotes surrounding arguments...
        String[] cleanArgs = cleanArgs( args );

        CommandLineParser parser = new GnuParser();

        return parser.parse( options, cleanArgs );
    }

    private String[] cleanArgs( String[] args )
    {
        List cleaned = new ArrayList();

        StringBuffer currentArg = null;

        for ( int i = 0; i < args.length; i++ )
        {
            String arg = args[i];

            boolean addedToBuffer = false;

            if ( arg.startsWith( "\"" ) )
            {
                // if we're in the process of building up another arg, push it and start over.
                // this is for the case: "-Dfoo=bar "-Dfoo2=bar two" (note the first unterminated quote)
                if ( currentArg != null )
                {
                    cleaned.add( currentArg.toString() );
                }

                // start building an argument here.
                currentArg = new StringBuffer( arg.substring( 1 ) );
                addedToBuffer = true;
            }

            // this has to be a separate "if" statement, to capture the case of: "-Dfoo=bar"
            if ( arg.endsWith( "\"" ) )
            {
                String cleanArgPart = arg.substring( 0, arg.length() - 1 );

                // if we're building an argument, keep doing so.
                if ( currentArg != null )
                {
                    // if this is the case of "-Dfoo=bar", then we need to adjust the buffer.
                    if ( addedToBuffer )
                    {
                        currentArg.setLength( currentArg.length() - 1 );
                    }
                    // otherwise, we trim the trailing " and append to the buffer.
                    else
                    {
                        // TODO: introducing a space here...not sure what else to do but collapse whitespace
                        currentArg.append( ' ' ).append( cleanArgPart );
                    }

                    cleaned.add( currentArg.toString() );
                }
                else
                {
                    cleaned.add( cleanArgPart );
                }

                currentArg = null;

                continue;
            }

            // if we haven't added this arg to the buffer, and we ARE building an argument
            // buffer, then append it with a preceding space...again, not sure what else to
            // do other than collapse whitespace.
            // NOTE: The case of a trailing quote is handled by nullifying the arg buffer.
            if ( !addedToBuffer )
            {
                if ( currentArg != null )
                {
                    currentArg.append( ' ' ).append( arg );
                }
                else
                {
                    cleaned.add( arg );
                }
            }
        }

        if ( currentArg != null )
        {
            cleaned.add( currentArg.toString() );
        }

        int cleanedSz = cleaned.size();

        String[] cleanArgs = null;

        if ( cleanedSz == 0 )
        {
            cleanArgs = args;
        }
        else
        {
            cleanArgs = (String[]) cleaned.toArray( new String[cleanedSz] );
        }

        return cleanArgs;
    }


    public void displayHelp()
    {
        System.out.println();

        HelpFormatter formatter = new HelpFormatter();

        formatter.printHelp( "mvn [options] [<goal(s)>] [<phase(s)>]", "\nOptions:", options, "\n" );
    }
}
