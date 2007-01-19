package org.apache.maven.profiles.activation;

import org.apache.maven.context.SystemBuildContext;
import org.apache.maven.model.Activation;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.StringUtils;

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

public class JdkPrefixProfileActivator
    extends DetectedProfileActivator
{
    
    public static final String JDK_VERSION = "java.version";

    public boolean isActive( Profile profile )
    {
        Activation activation = profile.getActivation();

        String jdk = activation.getJdk();
        
        boolean reverse = false;
        
        if ( jdk.startsWith( "!" ) )
        {
            reverse = true;
            jdk = jdk.substring( 1 );
        }
        
        SystemBuildContext systemContext = SystemBuildContext.getSystemBuildContext( getBuildContextManager(), true );
        String javaVersion = systemContext.getSystemProperty( JDK_VERSION );

        // null case is covered by canDetermineActivation(), so we can do a straight startsWith() here.
        boolean result = javaVersion.startsWith( jdk );
        
        if ( reverse )
        {
            return !result;
        }
        else
        {
            return result;
        }
    }

    protected boolean canDetectActivation( Profile profile )
    {
        return profile.getActivation() != null && StringUtils.isNotEmpty( profile.getActivation().getJdk() );
    }

}
