package org.apache.maven.artifact.repository.metadata;

/*
 * Copyright 2005 The Apache Software Foundation.
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

public class PluginMappingMetadata
    implements RepositoryMetadata
{
    private static final String PLUGIN_MAPPING_FILE = "plugins.xml";

    private final String groupId;

    public PluginMappingMetadata( String groupId )
    {
        this.groupId = groupId;
    }

    public String getRepositoryPath()
    {
        return groupId + "/" + PLUGIN_MAPPING_FILE;
    }

    public String toString()
    {
        return PLUGIN_MAPPING_FILE + " (plugin mappings) for group: \'" + groupId + "\'";
    }

}
