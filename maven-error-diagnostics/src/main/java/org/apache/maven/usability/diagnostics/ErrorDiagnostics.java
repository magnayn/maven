package org.apache.maven.usability.diagnostics;

import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.util.Iterator;
import java.util.List;

public class ErrorDiagnostics
    extends AbstractLogEnabled
    implements Contextualizable
{
    public static final String ROLE = ErrorDiagnostics.class.getName();
    
    private PlexusContainer container;

    private List errorDiagnosers;
    
    public void setErrorDiagnosers( List errorDiagnosers )
    {
        this.errorDiagnosers = errorDiagnosers;
    }
    
    public String diagnose( Throwable error )
    {
        List diags = errorDiagnosers;
        
        boolean releaseDiags = false;
        boolean errorProcessed = false;
        
        String message = null;
        
        try
        {
            if ( diags == null )
            {
                releaseDiags = true;
                
                try
                {
                    diags = container.lookupList( ErrorDiagnoser.ROLE );
                }
                catch ( ComponentLookupException e )
                {
                    getLogger().error( "Failed to lookup the list of error diagnosers.", e );
                }
            }
            
            if ( diags != null )
            {
                for ( Iterator it = diags.iterator(); it.hasNext(); )
                {
                    ErrorDiagnoser diagnoser = (ErrorDiagnoser) it.next();

                    if ( diagnoser.canDiagnose( error ) )
                    {
                        errorProcessed = true;

                        message = diagnose( error );

                        break;
                    }
                }
            }
        }
        finally
        {
            if ( releaseDiags && diags != null )
            {
                try
                {
                    container.releaseAll( diags );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().debug( "Failed to release error diagnoser list.", e );
                }
            }
            
            if ( !errorProcessed )
            {
                message = new PuntErrorDiagnoser().diagnose( error );
            }
        }
        
        return message;
    }
    
    public void contextualize( Context context )
        throws ContextException
    {
        this.container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }
    
    private static class PuntErrorDiagnoser implements ErrorDiagnoser
    {

        public boolean canDiagnose( Throwable error )
        {
            return true;
        }

        public String diagnose( Throwable error )
        {
            StringBuffer message = new StringBuffer();
            
            message.append( "Error: " ).append( error.getClass().getName() );
            message.append( "\nMessage: " ).append( error.getMessage() );
            
            Throwable root = DiagnosisUtils.getRootCause( error );
            
            if ( root != null && root != error )
            {
                message.append( "\n\nRoot Cause\n\n" );
                message.append( "Error: " ).append( root.getClass().getName() );
                message.append( "\nMessage: " ).append( root.getMessage() );
            }
            
            return message.toString();
        }
        
    }
}
