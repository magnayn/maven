package org.apache.maven.test;

import org.codehaus.surefire.SurefireBooter;

import java.io.File;
import java.util.List;

/**
 *
 *
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 *
 * @version $Id$
 *
 * @todo make version of junit and surefire configurable
 * @todo make report to be produced configurable
 */
public class SurefirePlugin
{
    public void execute( String mavenRepoLocal, String basedir, List includes, List excludes, String[] classpathElements )
        throws Exception
    {
        System.setProperty( "basedir", basedir );

        SurefireBooter surefireBooter = new SurefireBooter();

        surefireBooter.addBattery( "org.codehaus.surefire.battery.DirectoryBattery", new Object[]{basedir, includes, excludes} );

        surefireBooter.addClassPathUrl( new File( mavenRepoLocal, "junit/jars/junit-3.8.1.jar" ).getPath() );

        surefireBooter.addClassPathUrl( new File( mavenRepoLocal, "surefire/jars/surefire-1.0.jar" ).getPath() );

        surefireBooter.addClassPathUrl( new File( basedir, "target/classes" ).getPath() );

        surefireBooter.addClassPathUrl( new File( basedir, "target/test-classes" ).getPath() );

        for ( int i = 0; i < classpathElements.length; i++ )
        {
            surefireBooter.addClassPathUrl( classpathElements[i] );
        }

        surefireBooter.addReport( "org.codehaus.surefire.report.ConsoleReport" );

        surefireBooter.run();
    }
}
