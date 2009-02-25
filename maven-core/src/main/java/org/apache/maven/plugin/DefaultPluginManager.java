package org.apache.maven.plugin;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.ArtifactFilterManager;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.MultipleArtifactsNotFoundException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.lifecycle.statemgmt.StateManagementUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.MavenEvents;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.version.PluginVersionManager;
import org.apache.maven.plugin.version.PluginVersionNotFoundException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.DuplicateArtifactAttachmentException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.builder.PomInterpolatorTag;
import org.apache.maven.project.builder.PomTransformer;
import org.apache.maven.project.builder.ProjectUri;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.realm.MavenRealmManager;
import org.apache.maven.realm.RealmManagementException;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.repository.MavenRepositorySystem;
import org.apache.maven.shared.model.InterpolatorProperty;
import org.apache.maven.shared.model.ModelMarshaller;
import org.apache.maven.shared.model.ModelProperty;
import org.apache.maven.shared.model.ModelTransformerContext;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.component.repository.exception.ComponentRepositoryException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Component(role=PluginManager.class)
public class DefaultPluginManager
    extends AbstractLogEnabled
    implements PluginManager
{
    private static final List RESERVED_GROUP_IDS;

    static
    {
        List rgids = new ArrayList();

        rgids.add( StateManagementUtils.GROUP_ID );

        RESERVED_GROUP_IDS = rgids;
    }

    @Requirement
    protected PlexusContainer container;

    protected PluginDescriptorBuilder pluginDescriptorBuilder;

    @Requirement
    protected ArtifactFilterManager coreArtifactFilterManager;

    @Requirement
    protected PathTranslator pathTranslator;

    @Requirement
    protected MavenPluginCollector pluginCollector;

    @Requirement
    protected PluginVersionManager pluginVersionManager;

    @Requirement
    protected MavenRepositorySystem repositorySystem;

    @Requirement
    protected RuntimeInformation runtimeInformation;

    @Requirement
    protected MavenProjectBuilder mavenProjectBuilder;

    @Requirement
    protected PluginMappingManager pluginMappingManager;

    @Requirement
    private PluginManagerSupport pluginManagerSupport;

    @Requirement
    private PluginRepository pluginRepository;

    @Requirement
    private ProjectBuilder projectBuilder;

    public DefaultPluginManager()
    {
        pluginDescriptorBuilder = new PluginDescriptorBuilder();
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public Plugin getPluginDefinitionForPrefix( String prefix,
                                                MavenSession session,
                                                MavenProject project )
    {
        // TODO: since this is only used in the lifecycle executor, maybe it should be moved there? There is no other
        // use for the mapping manager in here
        return pluginMappingManager.getByPrefix( prefix, session.getPluginGroups(),
                                                 project.getRemoteArtifactRepositories(),
                                                 session.getLocalRepository() );
    }

    public PluginDescriptor verifyPlugin( Plugin plugin,
                                          MavenProject project,
                                          MavenSession session )
        throws ArtifactResolutionException, PluginVersionResolutionException,
        ArtifactNotFoundException, InvalidPluginException,
        PluginManagerException, PluginNotFoundException, PluginVersionNotFoundException
    {
        String pluginVersion = plugin.getVersion();

        // TODO: this should be possibly outside
        // All version-resolution logic has been moved to DefaultPluginVersionManager.
        getLogger().debug( "Resolving plugin: " + plugin.getKey() + " with version: " + pluginVersion );
        if ( ( pluginVersion == null ) || Artifact.LATEST_VERSION.equals( pluginVersion ) || Artifact.RELEASE_VERSION.equals( pluginVersion ) )
        {
            getLogger().debug( "Resolving version for plugin: " + plugin.getKey() );
            pluginVersion = pluginVersionManager.resolvePluginVersion( plugin.getGroupId(),
                                                                        plugin.getArtifactId(),
                                                                        project, session );
            plugin.setVersion( pluginVersion );

            getLogger().debug( "Resolved to version: " + pluginVersion );
        }

        return verifyVersionedPlugin( plugin, project, session );
    }

    private PluginDescriptor verifyVersionedPlugin( Plugin plugin,
                                                    MavenProject project,
                                                    MavenSession session )
        throws PluginVersionResolutionException, ArtifactNotFoundException,
        ArtifactResolutionException, InvalidPluginException,
        PluginManagerException, PluginNotFoundException
    {
        getLogger().debug( "In verifyVersionedPlugin for: " + plugin.getKey() );

        ArtifactRepository localRepository = session.getLocalRepository();

        // TODO: this might result in an artifact "RELEASE" being resolved continuously
        // FIXME: need to find out how a plugin gets marked as 'installed'
        // and no ChildContainer exists. The check for that below fixes
        // the 'Can't find plexus container for plugin: xxx' error.
        try
        {
            // if the groupId is internal, don't try to resolve it...
            if ( !RESERVED_GROUP_IDS.contains( plugin.getGroupId() ) )
            {
                Artifact pluginArtifact = pluginManagerSupport.resolvePluginArtifact( plugin, project, session );

                addPlugin( plugin, pluginArtifact, project, session );
            }
            else
            {
                getLogger().debug(
                                   "Skipping resolution for Maven built-in plugin: "
                                                   + plugin.getKey() );
            }

            project.addPlugin( plugin );
        }
        catch ( ArtifactNotFoundException e )
        {
            String groupId = plugin.getGroupId();

            String artifactId = plugin.getArtifactId();

            String version = plugin.getVersion();

            if ( ( groupId == null ) || ( artifactId == null ) || ( version == null ) )
            {
                throw new PluginNotFoundException( plugin, e );
            }
            else if ( groupId.equals( e.getGroupId() ) && artifactId.equals( e.getArtifactId() )
                      && version.equals( e.getVersion() ) && "maven-plugin".equals( e.getType() ) )
            {
                throw new PluginNotFoundException( plugin, e );
            }
            else
            {
                throw e;
            }
        }

        PluginDescriptor pluginDescriptor = pluginCollector.getPluginDescriptor( plugin );

        setDescriptorClassAndArtifactInfo( pluginDescriptor, project, session, new ArrayList() );

        return pluginDescriptor;
    }

    protected void addPlugin( Plugin plugin,
                              Artifact pluginArtifact,
                              MavenProject project,
                              MavenSession session )
        throws ArtifactNotFoundException, ArtifactResolutionException, PluginManagerException,
        InvalidPluginException
    {
        // ----------------------------------------------------------------------------
        // Get the dependencies for the Plugin
        // ----------------------------------------------------------------------------

        // the only Plugin instance which will have dependencies is the one specified in the project.
        // We need to look for a Plugin instance there, in case the instance we're using didn't come from
        // the project.
        Plugin projectPlugin = project.getPlugin( plugin.getKey() );

        if ( projectPlugin == null )
        {
            projectPlugin = plugin;
        }
        else if ( projectPlugin.getVersion() == null ||
                  Artifact.RELEASE_VERSION.equals(projectPlugin.getVersion()) ||
                  Artifact.LATEST_VERSION.equals(projectPlugin.getVersion()))
        {
            projectPlugin.setVersion( plugin.getVersion() );
        }

        Set<Artifact> artifactSet = getPluginArtifacts( pluginArtifact, projectPlugin, project, session.getLocalRepository() );

        List<Artifact> artifacts = ( artifactSet == null || artifactSet.isEmpty() )
                        ? new ArrayList<Artifact>() : new ArrayList<Artifact>( artifactSet );

        getLogger().debug( "Got plugin artifacts:\n\n" + artifacts );

        MavenRealmManager realmManager = session.getRealmManager();
        ClassRealm pluginRealm = realmManager.getPluginRealm( projectPlugin );
        if ( pluginRealm == null )
        {
            try
            {
                pluginRealm = realmManager.createPluginRealm( projectPlugin,
                                                              pluginArtifact,
                                                              artifacts,
                                                              coreArtifactFilterManager.getArtifactFilter() );

                getLogger().debug( "Created realm: " + pluginRealm + " for plugin: " + projectPlugin.getKey() );
            }
            catch ( RealmManagementException e )
            {
                throw new PluginContainerException( plugin,
                                                    "Failed to create realm for plugin '"
                                                                    + projectPlugin, e );
            }

            try
            {
                getLogger().debug( "Discovering components in realm: " + pluginRealm );

                container.discoverComponents( pluginRealm );
            }
            catch ( PlexusConfigurationException e )
            {
                throw new PluginContainerException( plugin, pluginRealm, "Error scanning plugin realm for components.", e );
            }
            catch ( ComponentRepositoryException e )
            {
                throw new PluginContainerException( plugin, pluginRealm, "Error scanning plugin realm for components.", e );
            }

            // ----------------------------------------------------------------------------
            // The PluginCollector will now know about the plugin we are trying to load
            // ----------------------------------------------------------------------------

            getLogger().debug(
                               "Checking for plugin descriptor for: " + projectPlugin.getKey()
                                               + " with version: " + projectPlugin.getVersion() + " in collector: " + pluginCollector );

            PluginDescriptor pluginDescriptor = pluginCollector.getPluginDescriptor( projectPlugin );

            if ( pluginDescriptor == null )
            {
                if ( ( pluginRealm != null ) && getLogger().isDebugEnabled() )
                {
                    getLogger().debug( "Plugin Realm: " );
                    pluginRealm.display();
                }

                getLogger().debug( "Removing invalid plugin realm." );
                realmManager.disposePluginRealm( projectPlugin );

                throw new PluginManagerException( projectPlugin, "The PluginDescriptor for the plugin "
                                                 + projectPlugin.getKey() + " was not found. Should have been in realm: " + pluginRealm, project );
            }

            pluginDescriptor.setPluginArtifact( pluginArtifact );

            getLogger().debug( "Realm for plugin: " + plugin.getKey() + ":\n" + pluginRealm );
        }
        else
        {
            List<Artifact> managedPluginArtifacts = realmManager.getPluginArtifacts( projectPlugin );

            if ( ( managedPluginArtifacts == null ) || ( managedPluginArtifacts.isEmpty() && !artifacts.isEmpty() ) )
            {
                realmManager.setPluginArtifacts( projectPlugin, artifacts );
            }
        }
    }

    private Set<Artifact> getPluginArtifacts( Artifact pluginArtifact,
                                    Plugin plugin,
                                    MavenProject project,
                                    ArtifactRepository localRepository )
        throws InvalidPluginException, ArtifactNotFoundException, ArtifactResolutionException
    {
        ArtifactFilter filter = new ScopeArtifactFilter( Artifact.SCOPE_RUNTIME_PLUS_SYSTEM );

        Set<Artifact> projectPluginDependencies;

        // The case where we have a plugin that can host multiple versions of a particular tool. Say the 
        // Antlr plugin which has many versions and you may want the plugin to execute with version 2.7.1 of
        // Antlr versus 2.7.2. In this case the project itself would specify dependencies within the plugin
        // element.
        
        try
        {
            projectPluginDependencies = repositorySystem.createArtifacts(
                                                                             plugin.getDependencies(),
                                                                             null,
                                                                             coreArtifactFilterManager.getCoreArtifactFilter(),
                                                                             project );            
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new InvalidPluginException( "Plugin '" + plugin + "' is invalid: "
                                              + e.getMessage(), e );
        }

        ResolutionGroup resolutionGroup;

        try
        {
            resolutionGroup = repositorySystem.retrieve( pluginArtifact, localRepository, project.getRemoteArtifactRepositories() );
        }
        catch ( ArtifactMetadataRetrievalException e )
        {
            throw new ArtifactResolutionException(
                                                   "Unable to download metadata from repository for plugin '"
                                                                   + pluginArtifact.getId() + "': "
                                                                   + e.getMessage(),
                                                   pluginArtifact, e );
        }

        /* get plugin managed versions */
        Map pluginManagedDependencies = new HashMap();
        try
        {
            MavenProject pluginProject =
                mavenProjectBuilder.buildFromRepository( pluginArtifact, project.getRemoteArtifactRepositories(), localRepository );
            
            if ( pluginProject != null )
            {
                pluginManagedDependencies = pluginProject.getManagedVersionMap();
            }
        }
        catch ( ProjectBuildingException e )
        {
            // this can't happen, it would have blowed up at artifactMetadataSource.retrieve()
        }

        Set<Artifact> dependencies = new LinkedHashSet<Artifact>();

        // resolve the plugin dependencies specified in <plugin><dependencies> first:
        dependencies.addAll( projectPluginDependencies );

        // followed by the plugin's default artifact set
        dependencies.addAll( resolutionGroup.getArtifacts() );

        Set repositories = new LinkedHashSet();

        repositories.addAll( resolutionGroup.getResolutionRepositories() );

        repositories.addAll( project.getRemoteArtifactRepositories() );

        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
            .setArtifact( pluginArtifact )
            .setArtifactDependencies( dependencies )
            .setLocalRepository( localRepository )
            .setRemoteRepostories( repositories.isEmpty() ? Collections.EMPTY_LIST : new ArrayList( repositories ) )
            .setManagedVersionMap( pluginManagedDependencies )
            .setFilter( filter )                
            .setMetadataSource( repositorySystem );        
        
        ArtifactResolutionResult result = repositorySystem.resolve( request );

        if ( result.hasErrorArtifactExceptions() )
        {
            throw result.getErrorArtifactExceptions().get( 0 );
        }
        
        Set<Artifact> resolved = new LinkedHashSet<Artifact>();

        for ( Iterator<Artifact> it = result.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact artifact = it.next();

            if ( !artifact.equals( pluginArtifact ) )
            {
                artifact = project.replaceWithActiveArtifact( artifact );
            }

            resolved.add( artifact );
        }

        getLogger().debug(
                           "Using the following artifacts for classpath of: "
                                           + pluginArtifact.getId() + ":\n\n"
                                           + resolved.toString().replace( ',', '\n' ) );

        return resolved;
    }

    // ----------------------------------------------------------------------
    // Mojo execution
    // ----------------------------------------------------------------------

    public void executeMojo( MavenProject project,
                             MojoExecution mojoExecution,
                             MavenSession session )
        throws ArtifactResolutionException, MojoFailureException,
        ArtifactNotFoundException, InvalidDependencyVersionException, PluginManagerException,
        PluginConfigurationException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        // NOTE: I'm putting these checks in here, since this is the central point of access for
        // anything that wants to execute a mojo.
        if ( mojoDescriptor.isProjectRequired() && !session.isUsingPOMsFromFilesystem() )
        {
            throw new PluginExecutionException( mojoExecution, project,
                                              "Cannot execute mojo: "
                                                              + mojoDescriptor.getGoal()
                                                              + ". It requires a project with an existing pom.xml, but the build is not using one." );
        }

        if ( mojoDescriptor.isOnlineRequired() && session.isOffline() )
        {
            // TODO: Should we error out, or simply warn and skip??
            throw new PluginExecutionException( mojoExecution, project,
                                              "Mojo: "
                                                              + mojoDescriptor.getGoal()
                                                              + " requires online mode for execution. Maven is currently offline." );
        }

        if ( mojoDescriptor.getDeprecated() != null )
        {
            getLogger().warn( "Mojo: " + mojoDescriptor.getGoal() + " is deprecated.\n" + mojoDescriptor.getDeprecated() );
        }

        Model model = project.getModel();
        pathTranslator.alignToBaseDirectory( model, project.getBasedir() );
        project.setBuild( model.getBuild() );

        if ( mojoDescriptor.isDependencyResolutionRequired() != null )
        {
            Collection projects;

            if ( mojoDescriptor.isAggregator() )
            {
                projects = session.getSortedProjects();
            }
            else
            {
                projects = Collections.singleton( project );
            }

            for ( Iterator i = projects.iterator(); i.hasNext(); )
            {
                MavenProject p = (MavenProject) i.next();

                resolveTransitiveDependencies( session,
                                               repositorySystem,
                                               mojoDescriptor.isDependencyResolutionRequired(),
                                               p,
                                               mojoDescriptor.isAggregator() );
            }

            downloadDependencies( project, session, repositorySystem );
        }

        String goalName = mojoDescriptor.getFullGoalName();

        Mojo mojo = null;

        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();

        Xpp3Dom dom = mojoExecution.getConfiguration();

        if ( dom != null )
        {
            try
            {
                List<InterpolatorProperty> interpolatorProperties = new ArrayList<InterpolatorProperty>();
                interpolatorProperties.addAll( InterpolatorProperty.toInterpolatorProperties(
                        session.getProjectBuilderConfiguration().getExecutionProperties(),
                        PomInterpolatorTag.EXECUTION_PROPERTIES.name()));
                interpolatorProperties.addAll( InterpolatorProperty.toInterpolatorProperties( session.getProjectBuilderConfiguration().getUserProperties(),
                        PomInterpolatorTag.USER_PROPERTIES.name()));
                String interpolatedDom  =
                        interpolateXmlString( String.valueOf( dom ), interpolatorProperties );
                dom = Xpp3DomBuilder.build( new StringReader( interpolatedDom ) );
            }
            catch ( XmlPullParserException e )
            {
                throw new PluginManagerException(
                                                  mojoDescriptor,
                                                  project,
                                                  "Failed to calculate concrete state for configuration of: "
                                                                  + mojoDescriptor.getHumanReadableKey(),
                                                  e );
            }
            catch ( IOException e )
            {
                throw new PluginManagerException(
                                                  mojoDescriptor,
                                                  project,
                                                  "Failed to calculate concrete state for configuration of: "
                                                                  + mojoDescriptor.getHumanReadableKey(),
                                                  e );
            }
        }

        // Event monitoring.
        String event = MavenEvents.MOJO_EXECUTION;
        EventDispatcher dispatcher = session.getEventDispatcher();

        String goalExecId = goalName;
        if ( mojoExecution.getExecutionId() != null )
        {
            goalExecId += " {execution: " + mojoExecution.getExecutionId() + "}";
        }

        // by this time, the pluginDescriptor has had the correct realm setup from getConfiguredMojo(..)
        ClassRealm pluginRealm;
        ClassRealm oldLookupRealm = container.getLookupRealm();
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

        List realmActions = new ArrayList();
        try
        {
            mojo = getConfiguredMojo( session, dom, project, false, mojoExecution, realmActions );

            dispatcher.dispatchStart( event, goalExecId );

            pluginRealm = pluginDescriptor.getClassRealm();

            getLogger().debug( "Setting context classloader for plugin to: " + pluginRealm.getId() + " (instance is: " + pluginRealm + ")" );

            Thread.currentThread().setContextClassLoader( pluginRealm );

            // NOTE: DuplicateArtifactAttachmentException is currently unchecked, so be careful removing this try/catch!
            // This is necessary to avoid creating compatibility problems for existing plugins that use
            // MavenProjectHelper.attachArtifact(..).
            try
            {
                mojo.execute();
            }
            catch( DuplicateArtifactAttachmentException e )
            {
                session.getEventDispatcher().dispatchError( event, goalExecId, e );

                throw new PluginExecutionException( mojoExecution, project, e );
            }

            // NEW: If the mojo that just executed is a report, store it in the LifecycleExecutionContext
            // for reference by future mojos.
            if ( mojo instanceof MavenReport )
            {
                session.addReport( mojoDescriptor, (MavenReport) mojo );
            }

            dispatcher.dispatchEnd( event, goalExecId );
        }
        catch ( MojoExecutionException e )
        {
            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw new PluginExecutionException( mojoExecution, project, e );
        }
        catch ( MojoFailureException e )
        {
            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        catch ( LinkageError e )
        {
            if ( getLogger().isFatalErrorEnabled() )
            {
                StringBuffer sb = new StringBuffer();
                sb.append( mojoDescriptor.getImplementation() ).append( "#execute() caused a linkage error (" );
                sb.append( e.getClass().getName() ).append( "). Check the realms:" );

                ClassRealm r = pluginDescriptor.getClassRealm();
                sb.append( "\n\nNOTE:\nPlugin realm is: " ).append( r.getId() );
                sb.append( "\nContainer realm is: " ).append( container.getContainerRealm().getId() );
                sb.append( "\n\n" );

                do
                {
                    sb.append( "Realm ID: " ).append( r.getId() ).append( '\n' );
                    for ( int i = 0; i < r.getURLs().length; i++ )
                    {
                        sb.append( "urls[" ).append( i ).append( "] = " ).append( r.getURLs()[i] );
                        if ( i != ( r.getURLs().length - 1 ) )
                        {
                            sb.append( '\n' );
                        }
                    }

                    sb.append( "\n\n" );
                    r = r.getParentRealm();
                }
                while( r != null );

                getLogger().fatalError(
                                       sb.toString(), e );
            }

            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        finally
        {
            if ( mojo != null )
            {
                try
                {
                    container.release( mojo );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().debug( "Error releasing mojo for: " + goalExecId, e );
                }
            }

            pluginDescriptor.setClassRealm( null );
            pluginDescriptor.setArtifacts( null );

            for ( Iterator it = realmActions.iterator(); it.hasNext(); )
            {
                PluginRealmAction action = (PluginRealmAction) it.next();
                action.undo();
            }

            if ( oldLookupRealm != null )
            {
                //container.setLookupRealm( oldLookupRealm );
                container.setLookupRealm( null );
            }

            Thread.currentThread().setContextClassLoader( oldClassLoader );
        }
    }

    private Plugin createDummyPlugin( PluginDescriptor pluginDescriptor )
    {
        Plugin plugin = new Plugin();
        plugin.setGroupId( pluginDescriptor.getGroupId() );
        plugin.setArtifactId( pluginDescriptor.getArtifactId() );
        plugin.setVersion( pluginDescriptor.getVersion() );

        return plugin;
    }

    public MavenReport getReport( MavenProject project,
                                  MojoExecution mojoExecution,
                                  MavenSession session )
        throws ArtifactNotFoundException, PluginConfigurationException, PluginManagerException,
        ArtifactResolutionException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
        PluginDescriptor descriptor = mojoDescriptor.getPluginDescriptor();
        Xpp3Dom dom = project.getReportConfiguration( descriptor.getGroupId(),
                                                      descriptor.getArtifactId(),
                                                      mojoExecution.getExecutionId() );
        if ( mojoExecution.getConfiguration() != null )
        {
            dom = Xpp3Dom.mergeXpp3Dom( dom, mojoExecution.getConfiguration() );
        }

        return (MavenReport) getConfiguredMojo( session, dom, project, true, mojoExecution, new ArrayList() );
    }

    public PluginDescriptor verifyReportPlugin( ReportPlugin reportPlugin,
                                                MavenProject project,
                                                MavenSession session )
        throws PluginVersionResolutionException, ArtifactResolutionException,
        ArtifactNotFoundException, InvalidPluginException,
        PluginManagerException, PluginNotFoundException, PluginVersionNotFoundException
    {
        String version = reportPlugin.getVersion();

        if ( version == null )
        {
            version = pluginVersionManager.resolveReportPluginVersion(
                                                                       reportPlugin.getGroupId(),
                                                                       reportPlugin.getArtifactId(),
                                                                       project, session );

            reportPlugin.setVersion( version );
        }

        Plugin forLookup = new Plugin();

        forLookup.setGroupId( reportPlugin.getGroupId() );
        forLookup.setArtifactId( reportPlugin.getArtifactId() );
        forLookup.setVersion( version );

        return verifyVersionedPlugin( forLookup, project, session );
    }

    private Mojo getConfiguredMojo( MavenSession session,
                                    Xpp3Dom dom,
                                    MavenProject project,
                                    boolean report,
                                    MojoExecution mojoExecution,
                                    List realmActions )
        throws PluginConfigurationException, PluginManagerException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();

        setDescriptorClassAndArtifactInfo( pluginDescriptor, project, session, realmActions );

        ClassRealm pluginRealm = pluginDescriptor.getClassRealm();

        if ( mojoDescriptor.isRequiresReports() )
        {
            Set reportDescriptors = session.getReportMojoDescriptors();

            if ( ( reportDescriptors != null ) && !reportDescriptors.isEmpty() )
            {
                for ( Iterator it = reportDescriptors.iterator(); it.hasNext(); )
                {
                    MojoDescriptor reportDescriptor = (MojoDescriptor) it.next();
                    setDescriptorClassAndArtifactInfo( reportDescriptor.getPluginDescriptor(), project, session, realmActions );
                }
            }
        }

        // We are forcing the use of the plugin realm for all lookups that might occur during
        // the lifecycle that is part of the lookup. Here we are specifically trying to keep
        // lookups that occur in contextualize calls in line with the right realm.
        container.setLookupRealm( pluginRealm );

        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader( pluginRealm );
        try
        {

        getLogger().debug(
                           "Looking up mojo " + mojoDescriptor.getRoleHint() + " in realm "
                                           + pluginRealm.getId() + " - descRealmId="
                                           + mojoDescriptor.getRealm() );

        Mojo mojo;
        try
        {
            mojo = container.lookup( Mojo.class, mojoDescriptor.getRoleHint() );
        }
        catch ( ComponentLookupException e )
        {
            throw new PluginContainerException( mojoDescriptor, pluginRealm, "Unable to find the mojo '"
                                              + mojoDescriptor.getRoleHint() + "' in the plugin '"
                                              + pluginDescriptor.getPluginLookupKey() + "'", e );
        }

        if ( mojo != null )
        {
            getLogger().debug(
                               "Looked up - " + mojo + " - "
                                               + mojo.getClass().getClassLoader() );
        }
        else
        {
            getLogger().warn( "No luck." );
        }

        if ( report && !( mojo instanceof MavenReport ) )
        {
            // TODO: the mojoDescriptor should actually capture this information so we don't get this far
            return null;
        }

        if ( mojo instanceof ContextEnabled )
        {
            Map pluginContext = session.getPluginContext( pluginDescriptor, project );

            pluginContext.put( "project", project );

            pluginContext.put( "pluginDescriptor", pluginDescriptor );

            ( (ContextEnabled) mojo ).setPluginContext( pluginContext );
        }

        mojo.setLog( new DefaultLog( getLogger() ) );

        XmlPlexusConfiguration pomConfiguration;

        if ( dom == null )
        {
            pomConfiguration = new XmlPlexusConfiguration( "configuration" );
        }
        else
        {
            pomConfiguration = new XmlPlexusConfiguration( dom );
        }

        // Validate against non-editable (@readonly) parameters, to make sure users aren't trying to
        // override in the POM.
        validatePomConfiguration( mojoDescriptor, pomConfiguration );

        PlexusConfiguration mergedConfiguration = mergeMojoConfiguration( pomConfiguration,
                                                                          mojoDescriptor );

        // TODO: plexus changes to make this more like the component descriptor so this can be used instead
        //            PlexusConfiguration mergedConfiguration = mergeConfiguration( pomConfiguration,
        //                                                                          mojoDescriptor.getConfiguration() );

        ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator(
                                                                                          session,
                                                                                          mojoExecution,
                                                                                          pathTranslator,
                                                                                          getLogger(),
                                                                                          session.getExecutionProperties() );

        PlexusConfiguration extractedMojoConfiguration = extractMojoConfiguration(
                                                                                   mergedConfiguration,
                                                                                   mojoDescriptor );

        checkDeprecatedParameters( mojoDescriptor, pomConfiguration );

        checkRequiredParameters( mojoDescriptor, extractedMojoConfiguration, expressionEvaluator );

        populatePluginFields( mojo, mojoDescriptor, extractedMojoConfiguration, expressionEvaluator );

        return mojo;

        } finally {
            Thread.currentThread().setContextClassLoader( oldClassLoader );
        }
    }

    private void checkDeprecatedParameters( MojoDescriptor mojoDescriptor,
                                            PlexusConfiguration extractedMojoConfiguration )
    {
        if ( ( extractedMojoConfiguration == null ) || ( extractedMojoConfiguration.getChildCount() < 1 ) )
        {
            return;
        }

        List parameters = mojoDescriptor.getParameters();
        if ( ( parameters != null ) && !parameters.isEmpty() )
        {
            for ( Iterator it = parameters.iterator(); it.hasNext(); )
            {
                Parameter param = (Parameter) it.next();

                if ( param.getDeprecated() != null )
                {
                    boolean warnOfDeprecation = false;
                    PlexusConfiguration child = extractedMojoConfiguration.getChild( param.getName() );
                    try
                    {
                        if ( ( child != null ) && ( child.getValue() != null ) )
                        {
                            warnOfDeprecation = true;
                        }
                        else if ( param.getAlias() != null)
                        {
                            child = extractedMojoConfiguration.getChild( param.getAlias() );
                            if ( ( child != null ) && ( child.getValue() != null ) )
                            {
                                warnOfDeprecation = true;
                            }
                        }
                    }
                    catch ( PlexusConfigurationException e )
                    {
                        // forget it, this is just for deprecation checking, after all...
                    }

                    if ( warnOfDeprecation )
                    {
                        StringBuffer buffer = new StringBuffer();
                        buffer.append( "In mojo: " ).append( mojoDescriptor.getGoal() ).append( ", parameter: " ).append( param.getName() );

                        if ( param.getAlias() != null )
                        {
                            buffer.append( " (alias: " ).append( param.getAlias() ).append( ")" );
                        }

                        buffer.append( " is deprecated:" ).append( "\n\n" ).append( param.getDeprecated() ).append( "\n" );

                        getLogger().warn( buffer.toString() );
                    }
                }
            }
        }
    }

    private void setDescriptorClassAndArtifactInfo( PluginDescriptor pluginDescriptor,
                                                    MavenProject project,
                                                    MavenSession session,
                                                    List realmActions )
    {
        MavenRealmManager realmManager = session.getRealmManager();

        ClassRealm projectRealm = realmManager.getProjectRealm( project.getGroupId(), project.getArtifactId(), project.getVersion() );
        if ( projectRealm == null )
        {
            getLogger().debug( "Realm for project: " + project.getId() + " not found. Using container realm instead." );

            projectRealm = container.getContainerRealm();
        }

        Plugin plugin = project.getPlugin( pluginDescriptor.getPluginLookupKey() );
        if ( plugin == null )
        {
            plugin = createDummyPlugin( pluginDescriptor );
        }

        ClassRealm pluginRealm = realmManager.getPluginRealm( plugin );

        if ( pluginRealm == null )
        {
            getLogger().debug( "Realm for plugin: " + pluginDescriptor.getId() + " not found. Using project realm instead." );

            pluginRealm = projectRealm;
            realmActions.add( new PluginRealmAction( pluginDescriptor ) );
        }
        else
        {
            pluginRealm.setParentRealm( projectRealm );
            realmActions.add( new PluginRealmAction( pluginDescriptor, pluginRealm ) );
        }

        getLogger().debug( "Setting realm for plugin descriptor: " + pluginDescriptor.getId() + " to: " + pluginRealm );
        pluginDescriptor.setClassRealm( pluginRealm );
        pluginDescriptor.setArtifacts( realmManager.getPluginArtifacts( plugin ) );
    }

    private PlexusConfiguration extractMojoConfiguration( PlexusConfiguration mergedConfiguration,
                                                          MojoDescriptor mojoDescriptor )
    {
        Map parameterMap = mojoDescriptor.getParameterMap();

        PlexusConfiguration[] mergedChildren = mergedConfiguration.getChildren();

        XmlPlexusConfiguration extractedConfiguration = new XmlPlexusConfiguration( "configuration" );

        for ( int i = 0; i < mergedChildren.length; i++ )
        {
            PlexusConfiguration child = mergedChildren[i];

            if ( parameterMap.containsKey( child.getName() ) )
            {
                extractedConfiguration.addChild( copyConfiguration( child ) );
            }
            else
            {
                // TODO: I defy anyone to find these messages in the '-X' output! Do we need a new log level?
                // ideally, this would be elevated above the true debug output, but below the default INFO level...
                // [BP] (2004-07-18): need to understand the context more but would prefer this could be either WARN or
                // removed - shouldn't need DEBUG to diagnose a problem most of the time.
                getLogger().debug(
                                   "*** WARNING: Configuration \'" + child.getName()
                                                   + "\' is not used in goal \'"
                                                   + mojoDescriptor.getFullGoalName()
                                                   + "; this may indicate a typo... ***" );
            }
        }

        return extractedConfiguration;
    }

    private void checkRequiredParameters( MojoDescriptor goal,
                                          PlexusConfiguration configuration,
                                          ExpressionEvaluator expressionEvaluator )
        throws PluginConfigurationException
    {
        // TODO: this should be built in to the configurator, as we presently double process the expressions

        List parameters = goal.getParameters();

        if ( parameters == null )
        {
            return;
        }

        List invalidParameters = new ArrayList();

        for ( int i = 0; i < parameters.size(); i++ )
        {
            Parameter parameter = (Parameter) parameters.get( i );

            if ( parameter.isRequired() )
            {
                // the key for the configuration map we're building.
                String key = parameter.getName();

                Object fieldValue = null;
                String expression = null;
                PlexusConfiguration value = configuration.getChild( key, false );
                try
                {
                    if ( value != null )
                    {
                        expression = value.getValue( null );

                        fieldValue = expressionEvaluator.evaluate( expression );

                        if ( fieldValue == null )
                        {
                            fieldValue = value.getAttribute( "default-value", null );
                        }
                    }

                    if ( ( fieldValue == null ) && StringUtils.isNotEmpty( parameter.getAlias() ) )
                    {
                        value = configuration.getChild( parameter.getAlias(), false );
                        if ( value != null )
                        {
                            expression = value.getValue( null );
                            fieldValue = expressionEvaluator.evaluate( expression );
                            if ( fieldValue == null )
                            {
                                fieldValue = value.getAttribute( "default-value", null );
                            }
                        }
                    }
                }
                catch ( ExpressionEvaluationException e )
                {
                    throw new PluginConfigurationException( goal.getPluginDescriptor(),
                                                            e.getMessage(), e );
                }

                // only mark as invalid if there are no child nodes
                if ( ( fieldValue == null )
                     && ( ( value == null ) || ( value.getChildCount() == 0 ) ) )
                {
                    parameter.setExpression( expression );
                    invalidParameters.add( parameter );
                }
            }
        }

        if ( !invalidParameters.isEmpty() )
        {
            throw new PluginParameterException( goal, invalidParameters );
        }
    }

    private void validatePomConfiguration( MojoDescriptor goal,
                                           PlexusConfiguration pomConfiguration )
        throws PluginConfigurationException
    {
        List parameters = goal.getParameters();

        if ( parameters == null )
        {
            return;
        }

        for ( int i = 0; i < parameters.size(); i++ )
        {
            Parameter parameter = (Parameter) parameters.get( i );

            // the key for the configuration map we're building.
            String key = parameter.getName();

            PlexusConfiguration value = pomConfiguration.getChild( key, false );

            if ( ( value == null ) && StringUtils.isNotEmpty( parameter.getAlias() ) )
            {
                key = parameter.getAlias();
                value = pomConfiguration.getChild( key, false );
            }

            if ( value != null )
            {
                // Make sure the parameter is either editable/configurable, or else is NOT specified in the POM
                if ( !parameter.isEditable() )
                {
                    StringBuffer errorMessage = new StringBuffer().append( "ERROR: Cannot override read-only parameter: " );
                    errorMessage.append( key );
                    errorMessage.append( " in goal: " ).append( goal.getFullGoalName() );

                    throw new PluginConfigurationException( goal.getPluginDescriptor(),
                                                            errorMessage.toString() );
                }

                String deprecated = parameter.getDeprecated();
                if ( StringUtils.isNotEmpty( deprecated ) )
                {
                    getLogger().warn( "DEPRECATED [" + parameter.getName() + "]: " + deprecated );
                }
            }
        }
    }

    private PlexusConfiguration mergeMojoConfiguration( XmlPlexusConfiguration fromPom,
                                                        MojoDescriptor mojoDescriptor )
    {
        XmlPlexusConfiguration result = new XmlPlexusConfiguration( fromPom.getName() );
        result.setValue( fromPom.getValue( null ) );

        if ( mojoDescriptor.getParameters() != null )
        {
            PlexusConfiguration fromMojo = mojoDescriptor.getMojoConfiguration();

            for ( Iterator it = mojoDescriptor.getParameters().iterator(); it.hasNext(); )
            {
                Parameter parameter = (Parameter) it.next();

                String paramName = parameter.getName();
                String alias = parameter.getAlias();
                String implementation = parameter.getImplementation();

                PlexusConfiguration pomConfig = fromPom.getChild( paramName );
                PlexusConfiguration aliased = null;

                if ( alias != null )
                {
                    aliased = fromPom.getChild( alias );
                }

                PlexusConfiguration mojoConfig = fromMojo.getChild( paramName, false );

                // first we'll merge configurations from the aliased and real params.
                // TODO: Is this the right thing to do?
                if ( aliased != null )
                {
                    if ( pomConfig == null )
                    {
                        pomConfig = new XmlPlexusConfiguration( paramName );
                    }

                    pomConfig = buildTopDownMergedConfiguration( pomConfig, aliased );
                }

                PlexusConfiguration toAdd = null;

                if ( pomConfig != null )
                {
                    pomConfig = buildTopDownMergedConfiguration( pomConfig, mojoConfig );

                    if ( StringUtils.isNotEmpty( pomConfig.getValue( null ) )
                         || ( pomConfig.getChildCount() > 0 ) )
                    {
                        toAdd = pomConfig;
                    }
                }

                if ( ( toAdd == null ) && ( mojoConfig != null ) )
                {
                    toAdd = copyConfiguration( mojoConfig );
                }

                if ( toAdd != null )
                {
                    if ( ( implementation != null )
                         && ( toAdd.getAttribute( "implementation", null ) == null ) )
                    {

                        XmlPlexusConfiguration implementationConf = new XmlPlexusConfiguration(
                                                                                                paramName );

                        implementationConf.setAttribute( "implementation",
                                                         parameter.getImplementation() );

                        toAdd = buildTopDownMergedConfiguration( toAdd, implementationConf );
                    }

                    result.addChild( toAdd );
                }
            }
        }
        return result;
    }

    private XmlPlexusConfiguration buildTopDownMergedConfiguration( PlexusConfiguration dominant,
                                                                    PlexusConfiguration recessive )
    {
        XmlPlexusConfiguration result = new XmlPlexusConfiguration( dominant.getName() );

        String value = dominant.getValue( null );

        if ( StringUtils.isEmpty( value ) && ( recessive != null ) )
        {
            value = recessive.getValue( null );
        }

        if ( StringUtils.isNotEmpty( value ) )
        {
            result.setValue( value );
        }

        String[] attributeNames = dominant.getAttributeNames();

        for ( int i = 0; i < attributeNames.length; i++ )
        {
            String attributeValue = dominant.getAttribute( attributeNames[i], null );

            result.setAttribute( attributeNames[i], attributeValue );
        }

        if ( recessive != null )
        {
            attributeNames = recessive.getAttributeNames();

            for ( int i = 0; i < attributeNames.length; i++ )
            {
                String attributeValue = recessive.getAttribute( attributeNames[i], null );
                // TODO: recessive seems to be dominant here?
                result.setAttribute( attributeNames[i], attributeValue );
            }
        }

        PlexusConfiguration[] children = dominant.getChildren();

        for ( int i = 0; i < children.length; i++ )
        {
            PlexusConfiguration childDom = children[i];
            PlexusConfiguration childRec = recessive == null ? null
                            : recessive.getChild( childDom.getName(), false );

            if ( childRec != null )
            {
                result.addChild( buildTopDownMergedConfiguration( childDom, childRec ) );
            }
            else
            { // FIXME: copy, or use reference?
                result.addChild( copyConfiguration( childDom ) );
            }
        }

        return result;
    }

    public static PlexusConfiguration copyConfiguration( PlexusConfiguration src )
    {
        // TODO: shouldn't be necessary
        XmlPlexusConfiguration dom = new XmlPlexusConfiguration( src.getName() );
        dom.setValue( src.getValue( null ) );

        String[] attributeNames = src.getAttributeNames();
        for ( int i = 0; i < attributeNames.length; i++ )
        {
            String attributeName = attributeNames[i];
            dom.setAttribute( attributeName, src.getAttribute( attributeName, null ) );
        }

        PlexusConfiguration[] children = src.getChildren();
        for ( int i = 0; i < children.length; i++ )
        {
            dom.addChild( copyConfiguration( children[i] ) );
        }

        return dom;
    }

    // ----------------------------------------------------------------------
    // Mojo Parameter Handling
    // ----------------------------------------------------------------------

    private void populatePluginFields( Mojo plugin,
                                       MojoDescriptor mojoDescriptor,
                                       PlexusConfiguration configuration,
                                       ExpressionEvaluator expressionEvaluator )
        throws PluginConfigurationException
    {
        ComponentConfigurator configurator = null;

        // TODO: What is the point in using the plugin realm here instead of the core realm?
        ClassRealm realm = mojoDescriptor.getPluginDescriptor().getClassRealm();

        try
        {
            String configuratorId = mojoDescriptor.getComponentConfigurator();

            // TODO: could the configuration be passed to lookup and the configurator known to plexus via the descriptor
            // so that this meethod could entirely be handled by a plexus lookup?
            if ( StringUtils.isNotEmpty( configuratorId ) )
            {
                configurator = container.lookup( ComponentConfigurator.class, configuratorId );
            }
            else
            {
                configurator = container.lookup( ComponentConfigurator.class, "basic" );
            }

            ConfigurationListener listener = new DebugConfigurationListener( getLogger() );

            getLogger().debug( "Configuring mojo '" + mojoDescriptor.getId() + "' with "
                               + ( configuratorId == null ? "basic" : configuratorId )
                               + " configurator -->" );

            // This needs to be able to use methods
            configurator.configureComponent( plugin, configuration, expressionEvaluator, realm, listener );

            getLogger().debug( "-- end configuration --" );
        }
        catch ( ComponentConfigurationException e )
        {
            throw new PluginConfigurationException(
                                                    mojoDescriptor.getPluginDescriptor(),
                                                    "Unable to parse the created DOM for plugin configuration",
                                                    e );
        }
        catch ( ComponentLookupException e )
        {
            throw new PluginConfigurationException(
                                                    mojoDescriptor.getPluginDescriptor(),
                                                    "Unable to retrieve component configurator for plugin configuration",
                                                    e );
        }
        catch ( LinkageError e )
        {
            if ( getLogger().isFatalErrorEnabled() )
            {
                getLogger().fatalError(
                                        configurator.getClass().getName() + "#configureComponent(...) caused a linkage error ("
                                            + e.getClass().getName() + ") and may be out-of-date. Check the realms:" );

                ClassRealm pluginRealm = mojoDescriptor.getPluginDescriptor().getClassRealm();
                StringBuffer sb = new StringBuffer();
                sb.append( "Plugin realm = " + pluginRealm.getId() ).append( '\n' );
                for ( int i = 0; i < pluginRealm.getURLs().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + pluginRealm.getURLs()[i] );
                    if ( i != ( pluginRealm.getURLs().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );

                ClassRealm containerRealm = container.getContainerRealm();
                sb = new StringBuffer();
                sb.append( "Container realm = " + containerRealm.getId() ).append( '\n' );
                for ( int i = 0; i < containerRealm.getURLs().length; i++ )
                {
                    sb.append( "urls[" + i + "] = " + containerRealm.getURLs()[i] );
                    if ( i != ( containerRealm.getURLs().length - 1 ) )
                    {
                        sb.append( '\n' );
                    }
                }
                getLogger().fatalError( sb.toString() );
            }

            throw new PluginConfigurationException(
                                                    mojoDescriptor.getPluginDescriptor(),
                                                    e.getClass().getName() + ": " + e.getMessage(),
                                                    new ComponentConfigurationException( e ) );
        }
        finally
        {
            if ( configurator != null )
            {
                try
                {
                    container.release( configurator );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().debug( "Failed to release plugin container - ignoring." );
                }
            }
        }
    }

    public static String createPluginParameterRequiredMessage( MojoDescriptor mojo,
                                                               Parameter parameter,
                                                               String expression )
    {
        StringBuffer message = new StringBuffer();

        message.append( "The '" );
        message.append( parameter.getName() );
        message.append( "' parameter is required for the execution of the " );
        message.append( mojo.getFullGoalName() );
        message.append( " mojo and cannot be null." );
        if ( expression != null )
        {
            message.append( " The retrieval expression was: " ).append( expression );
        }

        return message.toString();
    }

    // ----------------------------------------------------------------------
    // Artifact resolution
    // ----------------------------------------------------------------------

    protected void resolveTransitiveDependencies( MavenSession context,
                                                MavenRepositorySystem repositorySystem,
                                                String scope,
                                                MavenProject project,
                                                boolean isAggregator )
        throws ArtifactResolutionException, ArtifactNotFoundException,
        InvalidDependencyVersionException
    {
        // TODO: such a call in MavenMetadataSource too - packaging not really the intention of type
        Artifact artifact = repositorySystem.createBuildArtifact( project.getGroupId(),
                                                                 project.getArtifactId(),
                                                                 project.getVersion(),
                                                                 project.getPackaging() );

        // TODO: we don't need to resolve over and over again, as long as we are sure that the parameters are the same
        // check this with yourkit as a hot spot.
        // Don't recreate if already created - for effeciency, and because clover plugin adds to it
        if ( project.getDependencyArtifacts() == null )
        {
            // NOTE: Don't worry about covering this case with the error-reporter bindings...it's already handled by the project error reporter.
            project.setDependencyArtifacts( repositorySystem.createArtifacts( project.getDependencies(), null, null, project ) );
        }

        ArtifactFilter filter = new ScopeArtifactFilter( scope );

        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
            .setArtifact( project.getArtifact() )
            .setArtifactDependencies( project.getDependencyArtifacts() )
            .setLocalRepository( context.getLocalRepository() )
            .setRemoteRepostories( project.getRemoteArtifactRepositories() )
            .setManagedVersionMap( project.getManagedVersionMap() )
            .setFilter( filter )                
            .setMetadataSource( repositorySystem );
                 
        Set resolvedArtifacts;
        
        ArtifactResolutionResult result = repositorySystem.resolve( request );

        if ( result.hasErrorArtifactExceptions() )
        {
            /*
             
            only do this if we are an aggregating plugin: MNG-2277
            if the dependency doesn't yet exist but is in the reactor, then
            all we can do is warn and skip it. A better fix can be inserted into 2.1
            
            */
            if ( isAggregator && checkMissingArtifactsInReactor( context.getSortedProjects(), result.getMissingArtifacts() ) )
            {
                resolvedArtifacts = new LinkedHashSet( result.getArtifacts() );
            }
            else
            {
                //we can't find all the artifacts in the reactor so bubble the exception up.
                throw result.getErrorArtifactExceptions().get( 0 );
            }
        }
        
        project.setArtifacts( result.getArtifacts() );
    }

    /**
     * This method is checking to see if the artifacts that can't be resolved are all
     * part of this reactor. This is done to prevent a chicken or egg scenario with
     * fresh projects that have a plugin that is an aggregator and requires dependencies. See
     * MNG-2277 for more info.
     *
     * NOTE: If this happens, it most likely means the project-artifact for an
     * interproject dependency doesn't have a file yet (it hasn't been built yet).
     *
     * @param projects the sibling projects in the reactor
     * @param missing the artifacts that can't be found
     * @return true if ALL missing artifacts are found in the reactor.
     */
    private boolean checkMissingArtifactsInReactor( Collection projects, Collection missing )
    {
        Collection foundInReactor = new HashSet();
        Iterator iter = missing.iterator();
        while ( iter.hasNext() )
        {
            Artifact mArtifact = (Artifact) iter.next();
            Iterator pIter = projects.iterator();
            while ( pIter.hasNext() )
            {
                MavenProject p = (MavenProject) pIter.next();
                if ( p.getArtifactId().equals( mArtifact.getArtifactId() )
                     && p.getGroupId().equals( mArtifact.getGroupId() )
                     && p.getVersion().equals( mArtifact.getVersion() ) )
                {
                    //TODO: the packaging could be different, but the exception doesn't contain that info
                    //most likely it would be produced by the project we just found in the reactor since all
                    //the other info matches. Assume it's ok.
                    getLogger().warn( "The dependency: "
                                      + p.getId()
                                      + " can't be resolved but has been found in the reactor.\nThis dependency has been excluded from the plugin execution. You should rerun this mojo after executing mvn install.\n" );

                    //found it, move on.
                    foundInReactor.add( p );
                    break;
                }
            }
        }

        //if all of them have been found, we can continue.
        return foundInReactor.size() == missing.size();
    }

    // ----------------------------------------------------------------------
    // Artifact downloading
    // ----------------------------------------------------------------------

    private void downloadDependencies( MavenProject project,
                                       MavenSession context,
                                       MavenRepositorySystem repositorySystem )
        throws ArtifactResolutionException, ArtifactNotFoundException
    {
        ArtifactRepository localRepository = context.getLocalRepository();
        List remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        for ( Iterator it = project.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            repositorySystem.resolve( artifact, localRepository, remoteArtifactRepositories );
        }
    }

    public static void checkPlexusUtils( ResolutionGroup resolutionGroup,
                                         ArtifactFactory artifactFactory )
    {
        // ----------------------------------------------------------------------------
        // If the plugin already declares a dependency on plexus-utils then we're all
        // set as the plugin author is aware of its use. If we don't have a dependency
        // on plexus-utils then we must protect users from stupid plugin authors who
        // did not declare a direct dependency on plexus-utils because the version
        // Maven uses is hidden from downstream use. We will also bump up any
        // anything below 1.1 to 1.1 as this mimics the behaviour in 2.0.5 where
        // plexus-utils 1.1 was being forced into use.
        // ----------------------------------------------------------------------------

        VersionRange vr = null;

        try
        {
            vr = VersionRange.createFromVersionSpec( "[1.1,)" );
        }
        catch ( InvalidVersionSpecificationException e )
        {
            // Won't happen
        }

        boolean plexusUtilsPresent = false;

        for ( Iterator i = resolutionGroup.getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactId().equals( "plexus-utils" )
                 && vr.containsVersion( new DefaultArtifactVersion( a.getVersion() ) ) )
            {
                plexusUtilsPresent = true;

                break;
            }
        }

        if ( !plexusUtilsPresent )
        {
            // We will add plexus-utils as every plugin was getting this anyway from Maven itself. We will set the
            // version to the latest version we know that works as of the 2.0.6 release. We set the scope to runtime
            // as this is what's implicitly happening in 2.0.6.

            resolutionGroup.getArtifacts()
                           .add(
                                 artifactFactory.createArtifact( "org.codehaus.plexus",
                                                                 "plexus-utils", "1.1",
                                                                 Artifact.SCOPE_RUNTIME, "jar" ) );
        }
    }

    private static final class PluginRealmAction
    {
        private final PluginDescriptor pluginDescriptor;
        private final ClassRealm realmWithTransientParent;

        PluginRealmAction( PluginDescriptor pluginDescriptor )
        {
            this.pluginDescriptor = pluginDescriptor;
            realmWithTransientParent = null;
        }

        PluginRealmAction( PluginDescriptor pluginDescriptor, ClassRealm realmWithTransientParent )
        {
            this.pluginDescriptor = pluginDescriptor;
            this.realmWithTransientParent = realmWithTransientParent;
        }

        void undo()
        {
            pluginDescriptor.setArtifacts( null );
            pluginDescriptor.setClassRealm( null );

            if ( realmWithTransientParent != null )
            {
                realmWithTransientParent.setParentRealm( null );
            }
        }
    }

    private static String interpolateXmlString( String xml, List<InterpolatorProperty> interpolatorProperties )
        throws IOException
    {
        List<ModelProperty> modelProperties = ModelMarshaller.marshallXmlToModelProperties( new ByteArrayInputStream( xml.getBytes() ), ProjectUri.baseUri, PomTransformer.URIS );

        Map<String, String> aliases = new HashMap<String, String>();
        aliases.put( "project.", "pom." );

        List<InterpolatorProperty> ips = new ArrayList<InterpolatorProperty>( interpolatorProperties );
        ips.addAll( ModelTransformerContext.createInterpolatorProperties( modelProperties, ProjectUri.baseUri, aliases, PomInterpolatorTag.PROJECT_PROPERTIES.name(), false, false ) );

        for ( ModelProperty mp : modelProperties )
        {
            if ( mp.getUri().startsWith( ProjectUri.properties ) && mp.getValue() != null )
            {
                String uri = mp.getUri();
                ips.add( new InterpolatorProperty( "${" + uri.substring( uri.lastIndexOf( "/" ) + 1, uri.length() ) + "}", mp.getValue() ) );
            }
        }

        ModelTransformerContext.interpolateModelProperties( modelProperties, ips );
        return ModelMarshaller.unmarshalModelPropertiesToXml( modelProperties, ProjectUri.baseUri );
    }
}
