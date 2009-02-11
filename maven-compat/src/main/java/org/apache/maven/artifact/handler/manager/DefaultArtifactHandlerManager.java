package org.apache.maven.artifact.handler.manager;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import java.util.Map;
import java.util.Set;

/**
 * @author Jason van Zyl
 * @version $Id$
 */
@Component(role=ArtifactHandlerManager.class) 
public class DefaultArtifactHandlerManager
    implements ArtifactHandlerManager
{
    @Requirement(role=ArtifactHandler.class)    
    private Map<String,ArtifactHandler> artifactHandlers;

    public ArtifactHandler getArtifactHandler( String type )
    {
        ArtifactHandler handler = artifactHandlers.get( type );

        if ( handler == null )
        {
            handler = new DefaultArtifactHandler( type );
        }

        return handler;
    }

    public void addHandlers( Map<String,ArtifactHandler> handlers )
    {
        artifactHandlers.putAll( handlers );
    }

    public Set<String> getHandlerTypes()
    {
        return artifactHandlers.keySet();
    }
}
