package org.apache.maven.profiles;

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

import org.apache.maven.model.Activation;
import org.apache.maven.model.Profile;
import org.apache.maven.profiles.activation.DefaultProfileActivationContext;
import org.apache.maven.profiles.activation.ProfileActivationContext;
import org.apache.maven.profiles.activation.ProfileActivationException;
import org.apache.maven.profiles.activation.ProfileActivator;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class DefaultProfileManager
    implements ProfileManager
{
    private PlexusContainer container;

    private List activatedIds = new ArrayList();

    private List deactivatedIds = new ArrayList();

    private List defaultIds = new ArrayList();

    private Map profilesById = new LinkedHashMap();

    private ProfileActivationContext profileActivationContext;

    /**
     * the properties passed to the profile manager are the props that
     * are passed to maven, possibly containing profile activator properties
     *
     */
    public DefaultProfileManager( PlexusContainer container, ProfileActivationContext profileActivationContext )
    {
        this.container = container;
        this.profileActivationContext = profileActivationContext;
    }

    // TODO: Remove this, if possible. It uses system properties, which are not safe for IDE and other embedded environments.
    public DefaultProfileManager( PlexusContainer container )
    {
        this.container = container;
        profileActivationContext = new DefaultProfileActivationContext( System.getProperties(), false );
    }

    public ProfileActivationContext getProfileActivationContext()
    {
        return profileActivationContext;
    }

    public void setProfileActivationContext( ProfileActivationContext profileActivationContext )
    {
        this.profileActivationContext = profileActivationContext;
    }

    public Map getProfilesById()
    {
        return profilesById;
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#addProfile(org.apache.maven.model.Profile)
    */
    public void addProfile( Profile profile )
    {
        String profileId = profile.getId();

        Profile existing = (Profile) profilesById.get( profileId );
        if ( existing != null )
        {
            container.getLogger().warn( "Overriding profile: \'" + profileId + "\' (source: " + existing.getSource() +
                ") with new instance from source: " + profile.getSource() );
        }

        profilesById.put( profile.getId(), profile );

        Activation activation = profile.getActivation();

        if ( ( activation != null ) && activation.isActiveByDefault() )
        {
            activateAsDefault( profileId );
        }
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#explicitlyActivate(java.lang.String)
    */
    public void explicitlyActivate( String profileId )
    {
        if ( !activatedIds.contains( profileId ) )
        {
            container.getLogger().debug( "Profile with id: \'" + profileId + "\' has been explicitly activated." );

            activatedIds.add( profileId );
        }
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#explicitlyActivate(java.util.List)
    */
    public void explicitlyActivate( List profileIds )
    {
        for ( Iterator it = profileIds.iterator(); it.hasNext(); )
        {
            String profileId = (String) it.next();

            explicitlyActivate( profileId );
        }
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#explicitlyDeactivate(java.lang.String)
    */
    public void explicitlyDeactivate( String profileId )
    {
        if ( !deactivatedIds.contains( profileId ) )
        {
            container.getLogger().debug( "Profile with id: \'" + profileId + "\' has been explicitly deactivated." );

            deactivatedIds.add( profileId );
        }
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#explicitlyDeactivate(java.util.List)
    */
    public void explicitlyDeactivate( List profileIds )
    {
        for ( Iterator it = profileIds.iterator(); it.hasNext(); )
        {
            String profileId = (String) it.next();

            explicitlyDeactivate( profileId );
        }
    }

    /* (non-Javadoc)
    * @see org.apache.maven.profiles.ProfileManager#getActiveProfiles()
    */
    public List getActiveProfiles()
        throws ProfileActivationException
    {
        List activeFromPom = new ArrayList();
        List activeExternal = new ArrayList();

        for ( Iterator it = profilesById.entrySet().iterator(); it.hasNext(); )
        {
            Map.Entry entry = (Entry) it.next();

            String profileId = (String) entry.getKey();
            Profile profile = (Profile) entry.getValue();

            boolean shouldAdd = false;
            if ( activatedIds.contains( profileId ) )
            {
                shouldAdd = true;
            }
            else if ( !deactivatedIds.contains( profileId ) && isActive( profile, profileActivationContext ) )
            {
                shouldAdd = true;
            }

            if ( shouldAdd )
            {
                if ( "pom".equals( profile.getSource() ) )
                {
                    activeFromPom.add( profile );
                }
                else
                {
                    activeExternal.add( profile );
                }
            }
        }

        if ( activeFromPom.isEmpty() )
        {
            for ( Iterator it = defaultIds.iterator(); it.hasNext(); )
            {
                String profileId = (String) it.next();

                Profile profile = (Profile) profilesById.get( profileId );

                activeFromPom.add( profile );
            }
        }

        List allActive = new ArrayList( activeFromPom.size() + activeExternal.size() );

        allActive.addAll( activeExternal );
        allActive.addAll( activeFromPom );

        return allActive;
    }

    private boolean isActive( Profile profile, ProfileActivationContext context )
        throws ProfileActivationException
    {
        List activators = null;

        try
        {
            activators = container.lookupList( ProfileActivator.ROLE );

            for ( Iterator activatorIterator = activators.iterator(); activatorIterator.hasNext(); )
            {
                ProfileActivator activator = (ProfileActivator) activatorIterator.next();

                if ( activator.canDetermineActivation( profile, context ) )
                {
                    boolean result = activator.isActive( profile, context );

                    if ( result )
                    {
                        container.getLogger().debug( "Profile: " + profile.getId() + " is active. (source: " + profile.getSource() + ")" );
                    }

                    return result;
                }
            }

            return false;
        }
        catch ( ComponentLookupException e )
        {
            throw new ProfileActivationException( "Cannot retrieve list of profile activators.", e );
        }
        finally
        {
            container.getContext().put("SystemProperties", null);
            if ( activators != null )
            {
                try
                {
                    container.releaseAll( activators );
                }
                catch ( ComponentLifecycleException e )
                {
                    container.getLogger().debug( "Error releasing profile activators - ignoring.", e );
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.apache.maven.profiles.ProfileManager#addProfiles(java.util.List)
     */
    public void addProfiles( List profiles )
    {
        for ( Iterator it = profiles.iterator(); it.hasNext(); )
        {
            Profile profile = (Profile) it.next();

            addProfile( profile );
        }
    }

    public void activateAsDefault( String profileId )
    {
        if ( !defaultIds.contains( profileId ) )
        {
            defaultIds.add( profileId );
        }
    }

    public List getExplicitlyActivatedIds()
    {
        return activatedIds;
    }

    public List getExplicitlyDeactivatedIds()
    {
        return deactivatedIds;
    }

    public List getIdsActivatedByDefault()
    {
        return defaultIds;
    }
}
