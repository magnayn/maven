package org.apache.maven.model.converter.relocators;

/*
 * Copyright 2006 The Apache Software Foundation.
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
 * @author Dennis Lundberg
 * @version $Id: NoSuchPluginRelocatorException.java 409264 2006-05-24 23:13:13 +0000 (on, 24 maj 2006) dennisl $
 */
public class NoSuchPluginRelocatorException extends Exception
{
    private final String pluginRelocatorId;

    public NoSuchPluginRelocatorException( String pluginRelocatorId )
    {
        super( "No such plugin relocator '" + pluginRelocatorId + "'." );

        this.pluginRelocatorId = pluginRelocatorId;
    }

    public String getPluginRelocatorId()
    {
        return pluginRelocatorId;
    }
}
