package org.apache.maven.usability;

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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.usability.diagnostics.DiagnosisUtils;
import org.apache.maven.usability.diagnostics.ErrorDiagnoser;

public class MojoExecutionExceptionDiagnoser
    implements ErrorDiagnoser
{

    public boolean canDiagnose( Throwable error )
    {
        return DiagnosisUtils.containsInCausality( error, MojoExecutionException.class );
    }

    public String diagnose( Throwable error )
    {
        MojoExecutionException mee =
            (MojoExecutionException) DiagnosisUtils.getFromCausality( error, MojoExecutionException.class );

        StringBuffer message = new StringBuffer();

        Object source = mee.getSource();
        if ( source != null )
        {
            message.append( ": " ).append( mee.getSource() ).append( "\n" );
        }

        message.append( mee.getMessage() );

        String longMessage = mee.getLongMessage();
        if ( longMessage != null )
        {
            message.append( "\n\n" ).append( longMessage );
        }

        Throwable directCause = mee.getCause();

        if ( directCause != null )
        {
            message.append( "\n" );

            String directCauseMessage = directCause.getMessage();

            if ( directCauseMessage != null && mee.getMessage().indexOf( directCauseMessage ) < 0 )
            {
                message.append( "\nEmbedded error: " ).append( directCauseMessage );
            }

            DiagnosisUtils.appendRootCauseIfPresentAndUnique( directCause, message, false );
        }

        return message.toString();
    }

}
