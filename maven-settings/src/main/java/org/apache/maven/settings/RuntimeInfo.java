package org.apache.maven.settings;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeInfo
{

    private List locations = new ArrayList();
    
    // using Boolean for 3VL (null for not-set, otherwise override with value)
    private Boolean pluginUpdateForced;
    
    // using Boolean for 3VL (null, true-to-all, false-to-all)
    private Boolean applyToAllPluginUpdates;
    
//    private boolean pluginRegistryActive = true;
    
    // using Boolean for 3VL (null for not-set, otherwise override with value)
//    private Boolean checkLatest;
    
    private Map activeProfileToSourceLevel = new HashMap();
    
    private String localRepositorySourceLevel = TrackableBase.USER_LEVEL;
    
    private Map pluginGroupIdSourceLevels = new HashMap();
    
    private final Settings settings;

    public RuntimeInfo( Settings settings )
    {
        this.settings = settings;
    }
    
    public void addLocation( String path )
    {
        this.locations.add( path );
    }
    
    public List getLocations()
    {
        return locations;
    }
    
    public void setPluginUpdateOverride( Boolean pluginUpdateForced )
    {
        this.pluginUpdateForced = pluginUpdateForced;
    }
    
    public Boolean getPluginUpdateOverride()
    {
        return pluginUpdateForced;
    }

    public Boolean getApplyToAllPluginUpdates()
    {
        return applyToAllPluginUpdates;
    }

    public void setApplyToAllPluginUpdates( Boolean applyToAll )
    {
        this.applyToAllPluginUpdates = applyToAll;
    }
    
    public void setActiveProfileSourceLevel( String activeProfile, String sourceLevel )
    {
        activeProfileToSourceLevel.put( activeProfile, sourceLevel );
    }
    
    public String getSourceLevelForActiveProfile( String activeProfile )
    {
        String sourceLevel = (String) activeProfileToSourceLevel.get( activeProfile );
        
        if ( sourceLevel != null )
        {
            return sourceLevel;
        }
        else
        {
            return settings.getSourceLevel();
        }
    }
    
    public void setPluginGroupIdSourceLevel( String pluginGroupId, String sourceLevel )
    {
        pluginGroupIdSourceLevels.put( pluginGroupId, sourceLevel );
    }
    
    public String getSourceLevelForPluginGroupId( String pluginGroupId )
    {
        String sourceLevel = (String) pluginGroupIdSourceLevels.get( pluginGroupId );
        
        if ( sourceLevel != null )
        {
            return sourceLevel;
        }
        else
        {
            return settings.getSourceLevel();
        }
    }
    
    public void setLocalRepositorySourceLevel( String localRepoSourceLevel )
    {
        this.localRepositorySourceLevel = localRepoSourceLevel;
    }
    
    public String getLocalRepositorySourceLevel()
    {
        return localRepositorySourceLevel;
    }

}
