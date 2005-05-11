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

import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.TransitiveArtifactResolutionException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.ProjectBuildingException;

import java.util.List;
import java.util.Iterator;

public class ArtifactResolverDiagnoser
    implements ErrorDiagnoser
{

    public boolean canDiagnose( Throwable error )
    {
        return error instanceof ArtifactResolutionException;
    }

    public String diagnose( Throwable error )
    {
        Throwable root = traverseToRoot( error );

        String message = null;

        if ( root instanceof ProjectBuildingException )
        {
            StringBuffer messageBuffer = new StringBuffer();

            if ( causalityChainContains( error, TransitiveArtifactResolutionException.class ) )
            {
                messageBuffer.append(
                    "Error while transitively resolving artifacts (transitive path trace currently unavailable):\n\n" );
            }
            else
            {
                messageBuffer.append( "Error while resolving artifacts:\n\n" );
            }

            messageBuffer.append( "Root Error:\n  " ).append( root.getMessage() );

            message = messageBuffer.toString();
        }
        else
        {
            StringBuffer messageBuffer = new StringBuffer();

            messageBuffer.append( "Main Error:\n  " ).append( error.getMessage() );

            messageBuffer.append( "\n\nRoot error:\n  " ).append( root.getMessage() );

            message = messageBuffer.toString();
        }

        return message;
    }

    private boolean causalityChainContains( Throwable error, Class errorClass )
    {
        Throwable cause = error;

        boolean contains = false;

        while ( cause != null )
        {
            if ( errorClass.isInstance( cause ) )
            {
                contains = true;
                break;
            }

            cause = cause.getCause();
        }

        return contains;
    }

    private Throwable traverseToRoot( Throwable error )
    {
        Throwable potentialRoot = error;

        while ( potentialRoot.getCause() != null )
        {
            potentialRoot = potentialRoot.getCause();
        }

        return potentialRoot;
    }

}
