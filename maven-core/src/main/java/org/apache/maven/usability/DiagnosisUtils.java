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

public final class DiagnosisUtils
{
    private DiagnosisUtils()
    {
    }
    
    public static String getOfflineWarning()
    {
        return "\nNOTE: Maven is executing in offline mode. Any artifacts not already in your local\n" +
                "repository will be inaccessible.\n";
    }
    
    public static boolean containsInCausality( Throwable error, Class test )
    {
        Throwable cause = error;
        
        while( cause != null )
        {
            if( test.isInstance( cause ) )
            {
                return true;
            }
            
            cause = cause.getCause();
        }
        
        return false;
    }

    public static Throwable getRootCause( Throwable error )
    {
        Throwable cause = error;
        
        while( true )
        {
            Throwable nextCause = cause.getCause();
            
            if( nextCause == null )
            {
                break;
            }
            else
            {
                cause = nextCause;
            }
        }
        
        return cause;
    }

    public static Throwable getFromCausality( Throwable error, Class targetClass )
    {
        Throwable cause = error;
        
        while( cause != null )
        {
            if( targetClass.isInstance( cause ) )
            {
                return cause;
            }
            
            cause = cause.getCause();
        }
        
        return null;
    }
}
