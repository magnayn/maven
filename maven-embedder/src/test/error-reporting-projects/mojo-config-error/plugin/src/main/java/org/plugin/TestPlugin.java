package org.plugin;

import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

/**
 * @goal test
 */
public class TestPlugin
    implements Mojo
{

    private Log log;
    
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        throw new MojoExecutionException( "This is meant to fail." );
    }

    public Log getLog()
    {
        return log;
    }

    public void setLog( Log log )
    {
        this.log = log;
    }

}
