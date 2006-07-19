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
 * A parent <code>PluginRelocator</code> for SourceForge plugins.
 *
 * @author Dennis Lundberg
 * @version $Id: AbstractSourceForgePluginRelocator.java 411318 2006-06-02 22:34:35 +0000 (fr, 02 jun 2006) dennisl $
 */
public abstract class AbstractSourceForgePluginRelocator extends AbstractPluginRelocator
{
    /**
     * @see org.apache.maven.model.converter.relocators.AbstractPluginRelocator#getOldGroupId()
     */
    public String getOldGroupId()
    {
        return "maven-plugins";
    }
}
