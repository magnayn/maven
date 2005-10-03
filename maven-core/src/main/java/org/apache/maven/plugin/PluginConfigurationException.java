package org.apache.maven.plugin;

import org.apache.maven.plugin.descriptor.PluginDescriptor;

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

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @version $Id$
 */
public class PluginConfigurationException
    extends Exception
{
    private final PluginDescriptor pluginDescriptor;
    private String originalMessage;

    public PluginConfigurationException( PluginDescriptor pluginDescriptor, String message )
    {
        super( "Error configuring: " + pluginDescriptor.getPluginLookupKey() + ". Reason: " + message );
        this.pluginDescriptor = pluginDescriptor;
        this.originalMessage = message;
    }

    public PluginConfigurationException( PluginDescriptor pluginDescriptor, Throwable cause )
    {
        super( "Error configuring: " + pluginDescriptor.getPluginLookupKey() + ".", cause );
        this.pluginDescriptor = pluginDescriptor;
    }

    public PluginConfigurationException( PluginDescriptor pluginDescriptor, String message, Throwable cause )
    {
        super( "Error configuring: " + pluginDescriptor.getPluginLookupKey() + ". Reason: " + message, cause );
        this.pluginDescriptor = pluginDescriptor;
        this.originalMessage = message;
    }
    
    public PluginDescriptor getPluginDescriptor()
    {
        return pluginDescriptor;
    }
    
    public String getOriginalMessage()
    {
        return originalMessage;
    }
}
