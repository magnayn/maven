package org.apache.maven.realm;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.component.discovery.ComponentDiscoverer;
import org.codehaus.plexus.component.discovery.ComponentDiscovererManager;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryEvent;
import org.codehaus.plexus.component.discovery.ComponentDiscoveryListener;
import org.codehaus.plexus.component.discovery.DefaultComponentDiscoverer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.ComponentSetDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.logging.Logger;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultMavenRealmManager
    implements MavenRealmManager
{

    private Map pluginArtifacts = new HashMap();
    private Set managedRealmIds = new HashSet();

    private final ClassWorld world;
    private final PlexusContainer container;
    private final Logger logger;

    public DefaultMavenRealmManager( PlexusContainer container,
                                     Logger logger )
    {
        world = container.getContainerRealm().getWorld();
        this.container = container;
        this.logger = logger;
    }

    //mkleint: the clearing is fine for sequenced operations. Even though the
    // MavenRealmManager is associated with request, the paralel execution will
    // eventualy fail as the ClassWorld and PlexusContainer are not meant for
    // multithreaded environment.
    public void clear()
    {
        Collection realms = new HashSet( world.getRealms() );
        for ( Iterator it = realms.iterator(); it.hasNext(); )
        {
            ClassRealm realm = (ClassRealm) it.next();
            String id = realm.getId();

            if ( managedRealmIds.contains( id ) )
            {
                try
                {
                    logger.debug( "disposing managed ClassRealm with id: " + id );
                    world.disposeRealm( id );

                    logger.debug( "dissociating all components from managed ClassRealm with id: " + id );
                    container.removeComponentRealm( realm );
                }
                catch ( NoSuchRealmException e )
                {
                    // cannot happen.
                }
                catch ( PlexusContainerException e )
                {
                    logger.debug( "Error while dissociating: " + e.getMessage(), e );
                }
            }
        }

        managedRealmIds.clear();
        pluginArtifacts.clear();
    }

    public boolean hasExtensionRealm( Artifact extensionArtifact )
    {
        String id = RealmUtils.createExtensionRealmId( extensionArtifact );
        try
        {
            return world.getRealm( id ) != null;
        }
        catch ( NoSuchRealmException e )
        {
            return false;
        }
    }

    public ClassRealm createExtensionRealm( Artifact extensionArtifact,
                                            Collection artifacts )
        throws RealmManagementException
    {
        String id = RealmUtils.createExtensionRealmId( extensionArtifact );
        ClassRealm realm;
        try
        {
            realm = container.getContainerRealm().createChildRealm( id );
            managedRealmIds.add( id );
        }
        catch ( DuplicateRealmException e )
        {
            throw new RealmManagementException( id, "Extension realm: " + id + " already exists.",
                                                e );
        }

        populateRealm( id, realm, extensionArtifact, artifacts );

        return realm;
    }

    public void importExtensionsIntoProjectRealm( String projectGroupId,
                                                  String projectArtifactId,
                                                  String projectVersion,
                                                  Artifact extensionArtifact )
        throws RealmManagementException
    {
        String extensionRealmId = RealmUtils.createExtensionRealmId( extensionArtifact );

        if ( extensionArtifact.getFile() == null )
        {
            throw new RealmManagementException( extensionRealmId, "Cannot import project extensions; extension artifact has no associated file that can be scanned for extension components (extension: " + extensionArtifact.getId() + ")" );
        }

        ClassWorld discoveryWorld = new ClassWorld();

        List componentSetDescriptors;
        try
        {
            // Create an entire new ClassWorld, ClassRealm for discovering
            // the immediate components of the extension artifact, so we don't pollute the
            // container with component descriptors or realms that don't have any meaning beyond discovery.
            ClassRealm discoveryRealm;
            try
            {
                discoveryRealm = discoveryWorld.newRealm( "discovery: " + extensionRealmId );
            }
            catch ( DuplicateRealmException e )
            {
                throw new RealmManagementException( extensionRealmId, "Unable to create temporary ClassRealm for local-component discovery.", e );
            }

            try
            {
                discoveryRealm.addURL( extensionArtifact.getFile().toURL() );
            }
            catch ( MalformedURLException e )
            {
                throw new RealmManagementException( extensionRealmId, extensionArtifact, "Unable to generate URL from extension artifact file: " + extensionArtifact.getFile() + " for local-component discovery.", e );
            }

            ComponentDiscoverer discoverer = new DefaultComponentDiscoverer();
            discoverer.setManager( new DummyDiscovererManager() );

            try
            {
                // Find the extension component descriptors that exist ONLY in the immediate extension
                // artifact...this prevents us from adding plexus-archiver components to the mix, for instance,
                // when the extension uses that dependency.
                componentSetDescriptors = discoverer.findComponents( container.getContext(), discoveryRealm );
            }
            catch ( PlexusConfigurationException e )
            {
                throw new RealmManagementException( extensionRealmId, "Unable to discover components in extension artifact: " + extensionArtifact.getId(), e );
            }

            ClassRealm realm = getProjectRealm( projectGroupId, projectArtifactId, projectVersion, true );

            for ( Iterator it = componentSetDescriptors.iterator(); it.hasNext(); )
            {
                ComponentSetDescriptor compSet = (ComponentSetDescriptor) it.next();
                for ( Iterator compIt = compSet.getComponents().iterator(); compIt.hasNext(); )
                {
                    // For each component in the extension artifact:
                    ComponentDescriptor comp = (ComponentDescriptor) compIt.next();
                    String implementation = comp.getImplementation();

                    try
                    {
                        logger.debug( "Importing: " + implementation + "\nwith role: " + comp.getRole() + "\nand hint: " + comp.getRoleHint() + "\nfrom extension realm: " + extensionRealmId + "\nto project realm: " + realm.getId() );

                        // Import the extension component's implementation class into the project-level
                        // realm.
                        realm.importFrom( extensionRealmId, implementation );

                        // Set the realmId to be used in looking up this extension component to the
                        // project-level realm, since we now have a restricted import
                        // that allows most of the extension to stay hidden, and the
                        // specific local extension components are still accessible
                        // from the project-level realm.
                        comp.setRealmId( realm.getId() );

                        // Finally, add the extension component's descriptor (with projectRealm
                        // set as the lookup realm) to the container.
                        container.addComponentDescriptor( comp );
                    }
                    catch ( NoSuchRealmException e )
                    {
                        throw new RealmManagementException( extensionRealmId, "Failed to create import for component: " + implementation + " from extension realm: " + extensionRealmId + " to project realm: " + realm.getId(), e );
                    }
                    catch ( ComponentRepositoryException e )
                    {
                        String projectId = RealmUtils.createProjectId( projectGroupId, projectArtifactId, projectVersion );
                        throw new RealmManagementException( extensionRealmId, "Unable to discover components from imports to project: " + projectId + " from extension artifact: " + extensionArtifact.getId(), e );
                    }
                }
            }
        }
        finally
        {
            Collection realms = discoveryWorld.getRealms();
            for ( Iterator it = realms.iterator(); it.hasNext(); )
            {
                ClassRealm realm = (ClassRealm) it.next();
                try
                {
                    discoveryWorld.disposeRealm( realm.getId() );
                }
                catch ( NoSuchRealmException e )
                {
                }
            }
        }

    }

    public ClassRealm getProjectRealm( String projectGroupId, String projectArtifactId, String projectVersion )
    {
        return getProjectRealm( projectGroupId, projectArtifactId, projectVersion, false );
    }

    private ClassRealm getProjectRealm( String projectGroupId, String projectArtifactId, String projectVersion, boolean create )
    {
        String id = RealmUtils.createProjectId( projectGroupId, projectArtifactId, projectVersion );

        ClassRealm realm = null;
        try
        {
            realm = world.getRealm( id );
        }
        catch ( NoSuchRealmException e )
        {
            if ( create )
            {
                try
                {
                    realm = container.getContainerRealm().createChildRealm( id );
                    managedRealmIds.add( id );
                }
                catch ( DuplicateRealmException duplicateError )
                {
                    // won't happen.
                }
            }
        }

        return realm;
    }

    private static final class DummyDiscovererManager implements ComponentDiscovererManager
    {

        public void fireComponentDiscoveryEvent( ComponentDiscoveryEvent arg0 )
        {
        }

        public List getComponentDiscoverers()
        {
            return null;
        }

        public Map getComponentDiscoveryListeners()
        {
            return null;
        }

        public List getListeners()
        {
            return null;
        }

        public void initialize()
        {
        }

        public void registerComponentDiscoveryListener( ComponentDiscoveryListener l )
        {
        }

        public void removeComponentDiscoveryListener( ComponentDiscoveryListener l )
        {
        }

    }

    public ClassRealm getPluginRealm( Plugin plugin )
    {
        String id = RealmUtils.createPluginRealmId( plugin );

        logger.debug( "Retrieving realm for plugin with id: " + id );

        try
        {
            return world.getRealm( id );
        }
        catch ( NoSuchRealmException e )
        {
            return null;
        }
    }

    public void disposePluginRealm( Plugin plugin )
    {
        String id = RealmUtils.createPluginRealmId( plugin );

        logger.debug( "Disposing realm for plugin with id: " + id );

        try
        {
            world.disposeRealm( id );
        }
        catch ( NoSuchRealmException e )
        {
            logger.debug( "Plugin realm: " + id + " didn't exist in ClassWorld instance." );
        }

        managedRealmIds.remove( id );
        pluginArtifacts.remove( id );
    }

    public ClassRealm createPluginRealm( Plugin plugin,
                                          Artifact pluginArtifact,
                                          Collection artifacts )
        throws RealmManagementException
    {
        String id = RealmUtils.createPluginRealmId( plugin );

        logger.debug( "Creating realm for plugin with id: " + id );

        ClassRealm realm;
        try
        {
            realm = world.newRealm( id );
            managedRealmIds.add( id );
        }
        catch ( DuplicateRealmException e )
        {
            throw new RealmManagementException( id, "Plugin realm: " + id + " already exists.",
                                                e );
        }

        populateRealm( id, realm, pluginArtifact, artifacts );

        logger.debug( "Saving artifacts:\n\n" + artifacts + "\n\nfor plugin: " + id );
        pluginArtifacts.put( id, artifacts );

        return realm;
    }

    private void populateRealm( String id,
                                ClassRealm realm,
                                Artifact mainArtifact,
                                Collection artifacts )
        throws RealmManagementException
    {
        if ( !artifacts.contains( mainArtifact ) )
        {
            try
            {
                realm.addURL( mainArtifact.getFile().toURI().toURL() );
            }
            catch ( MalformedURLException e )
            {
                throw new RealmManagementException( id, mainArtifact, "Invalid URL for artifact file: "
                                                                  + mainArtifact.getFile()
                                                                  + " to be used in realm: " + id
                                                                  + ".", e );
            }
        }

        for ( Iterator it = artifacts.iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();
            try
            {
                realm.addURL( artifact.getFile().toURI().toURL() );
            }
            catch ( MalformedURLException e )
            {
                throw new RealmManagementException( id, artifact, "Invalid URL for artifact file: "
                                                                  + artifact.getFile()
                                                                  + " to be used in realm: " + id
                                                                  + ".", e );
            }
        }
    }

    public List getPluginArtifacts( Plugin plugin )
    {
        String id = RealmUtils.createPluginRealmId( plugin );

        logger.debug( "Getting artifacts used in realm for plugin with id: " + id );

        Collection artifacts = (Collection) pluginArtifacts.get( id );

        if ( artifacts != null )
        {
            logger.debug( "Returning artifacts:\n\n" + artifacts + "\n\nfor plugin: " + id );
            return new ArrayList( artifacts );
        }

        logger.debug( "Found no artifacts for plugin: " + id );
        return null;
    }

    public void setPluginArtifacts( Plugin plugin,
                                    Collection artifacts )
    {
        String id = RealmUtils.createPluginRealmId( plugin );

        logger.debug( "Setting artifact collection for plugin with id: " + id + " to:\n\n" + artifacts );

        pluginArtifacts.put( id, artifacts );
    }
}