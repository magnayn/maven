package org.apache.maven.plugin;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExclusionSetFilter;
import org.apache.maven.artifact.resolver.filter.InversionArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.monitor.event.EventDispatcher;
import org.apache.maven.monitor.event.MavenEvents;
import org.apache.maven.monitor.logging.DefaultLog;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.version.PluginVersionManager;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.LoggerManager;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultPluginManager
    extends AbstractLogEnabled
    implements PluginManager, Initializable, Contextualizable
{
    protected PlexusContainer container;

    protected PluginDescriptorBuilder pluginDescriptorBuilder;

    protected ArtifactFilter artifactFilter;

    private Log mojoLogger;

    // component requirements
    protected PathTranslator pathTranslator;
    
    protected MavenPluginCollector pluginCollector;
    
    protected PluginVersionManager pluginVersionManager;
    
    protected ArtifactFactory artifactFactory;

    protected ArtifactResolver artifactResolver;

    protected MavenProjectBuilder mavenProjectBuilder;
    // END component requirements

    public DefaultPluginManager()
    {
        pluginDescriptorBuilder = new PluginDescriptorBuilder();
    }

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public PluginDescriptor getPluginDescriptorForPrefix( String prefix )
    {
        return pluginCollector.getPluginDescriptorForPrefix( prefix );
    }
    
    public PluginDescriptor verifyPlugin( Plugin plugin, MavenProject project, Settings settings, 
                                          ArtifactRepository localRepository )
        throws ArtifactResolutionException, PluginManagerException, PluginVersionResolutionException
    {
        // TODO: this should be possibly outside
        // [HTTP-301] All version-resolution logic has been moved to DefaultPluginVersionManager. :)
        if ( plugin.getVersion() == null )
        {
            String groupId = plugin.getGroupId();
            String artifactId = plugin.getArtifactId();
            
            plugin.setVersion( pluginVersionManager.resolvePluginVersion( groupId, artifactId, project, settings,
                                                                 localRepository ) );
        }

        // TODO: this might result in an artifact "RELEASE" being resolved continuously
        if ( !pluginCollector.isPluginInstalled( plugin ) )
        {
            try
            {
                Artifact pluginArtifact = artifactFactory.createArtifact( plugin.getGroupId(), 
                                                                          plugin.getArtifactId(), 
                                                                          plugin.getVersion(),
                                                                          Artifact.SCOPE_RUNTIME,
                                                                          MojoDescriptor.MAVEN_PLUGIN );
    
                // TODO: [jc; 2005-july-06] what's this for?
                //plugin.setVersion( pluginArtifact.getBaseVersion() );
                
                addPlugin( plugin, pluginArtifact, project, localRepository );
                
                project.addPlugin( plugin );
            }
            catch ( PlexusContainerException e )
            {
                throw new PluginManagerException(
                    "Error occurred in the artifact container attempting to download plugin " + plugin.getKey(), e );
            }
            catch ( ArtifactResolutionException e )
            {
                String groupId = plugin.getGroupId();
                String artifactId = plugin.getArtifactId();
                String version = plugin.getVersion();
                
                if (
                    ( groupId == null || artifactId == null || version == null ||
                        ( groupId.equals( e.getGroupId() ) && artifactId.equals( e.getArtifactId() ) &&
                            version.equals( e.getVersion() ) ) ) && "maven-plugin".equals( e.getType() ) )
                {
                    throw new PluginNotFoundException( e );
                }
                else
                {
                    throw e;
                }
            }
        }
        
        return pluginCollector.getPluginDescriptor( plugin );
    }

    protected void addPlugin( Plugin plugin, Artifact pluginArtifact, MavenProject project,
                              ArtifactRepository localRepository )
        throws ArtifactResolutionException, PlexusContainerException
    {
        artifactResolver.resolve( pluginArtifact, project.getPluginArtifactRepositories(), localRepository );

        PlexusContainer child = container.createChildContainer( plugin.getKey(), Collections
            .singletonList( pluginArtifact.getFile() ), Collections.EMPTY_MAP, Collections.singletonList( pluginCollector ) );

        // this plugin's descriptor should have been discovered in the child creation, so we should be able to
        // circle around and set the artifacts and class realm
        PluginDescriptor addedPlugin = pluginCollector.getPluginDescriptor( plugin );

        addedPlugin.setClassRealm( child.getContainerRealm() );

        // we're only setting the plugin's artifact itself as the artifact list, to allow it to be retrieved
        // later when the plugin is first invoked. Retrieving this artifact will in turn allow us to
        // transitively resolve its dependencies, and add them to the plugin container...
        addedPlugin.setArtifacts( Collections.singletonList( pluginArtifact ) );
    }

    // ----------------------------------------------------------------------
    // Mojo execution
    // ----------------------------------------------------------------------

    public void executeMojo( MavenProject project, MojoExecution mojoExecution, MavenSession session )
        throws ArtifactResolutionException, PluginManagerException, MojoExecutionException
    {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        if ( mojoDescriptor.isDependencyResolutionRequired() != null )
        {

            resolveTransitiveDependencies( session, artifactResolver, mavenProjectBuilder, mojoDescriptor
                .isDependencyResolutionRequired(), artifactFactory, project );
            
            downloadDependencies( project, session, artifactResolver );
        }

        String goalName = mojoDescriptor.getFullGoalName();

        Mojo plugin = null;

        try
        {
            PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();
            String goalId = mojoDescriptor.getGoal();
            String groupId = pluginDescriptor.getGroupId();
            String artifactId = pluginDescriptor.getArtifactId();
            String executionId = mojoExecution.getExecutionId();
            Xpp3Dom dom = project.getGoalConfiguration( groupId, artifactId, executionId, goalId );
            Xpp3Dom reportDom = project.getReportConfiguration( groupId, artifactId, executionId );
            dom = Xpp3Dom.mergeXpp3Dom( dom, reportDom );
            if ( mojoExecution.getConfiguration() != null )
            {
                dom = Xpp3Dom.mergeXpp3Dom( dom, mojoExecution.getConfiguration() );
            }

            plugin = getConfiguredMojo( mojoDescriptor, session, dom, project );
        }
        catch ( PluginConfigurationException e )
        {
            String msg = "Error configuring plugin for execution of '" + goalName + "'.";
            throw new MojoExecutionException( msg, e );
        }
        catch ( ComponentLookupException e )
        {
            throw new MojoExecutionException( "Error looking up plugin: ", e );
        }

        // !! This is ripe for refactoring to an aspect.
        // Event monitoring.
        String event = MavenEvents.MOJO_EXECUTION;
        EventDispatcher dispatcher = session.getEventDispatcher();

        String goalExecId = goalName;

        if ( mojoExecution.getExecutionId() != null )
        {
            goalExecId += " {execution: " + mojoExecution.getExecutionId() + "}";
        }

        dispatcher.dispatchStart( event, goalExecId );

        try
        {
            plugin.execute();

            dispatcher.dispatchEnd( event, goalExecId );
        }
        catch ( MojoExecutionException e )
        {
            session.getEventDispatcher().dispatchError( event, goalExecId, e );

            throw e;
        }
        finally
        {
            try
            {
                PlexusContainer pluginContainer = getPluginContainer( mojoDescriptor.getPluginDescriptor() );

                pluginContainer.release( plugin );
            }
            catch ( ComponentLifecycleException e )
            {
                if ( getLogger().isErrorEnabled() )
                {
                    getLogger().error( "Error releasing plugin - ignoring.", e );
                }
            }
        }
    }

    public List getReports( ReportPlugin reportPlugin, ReportSet reportSet, MavenProject project,
                            MavenSession session, ArtifactRepository localRepository )
        throws PluginManagerException, PluginVersionResolutionException, PluginConfigurationException,
        ArtifactResolutionException
    {
        Plugin forLookup = new Plugin();
        forLookup.setGroupId( reportPlugin.getGroupId() );
        forLookup.setArtifactId( reportPlugin.getArtifactId() );
        forLookup.setVersion( reportPlugin.getVersion() );
        
        PluginDescriptor pluginDescriptor = verifyPlugin( forLookup, project, session.getSettings(), localRepository );
        
        List reports = new ArrayList();
        for ( Iterator i = pluginDescriptor.getMojos().iterator(); i.hasNext(); )
        {
            MojoDescriptor mojoDescriptor = (MojoDescriptor) i.next();

            // TODO: check ID is correct for reports
            // TODO: this returns mojos that aren't reports
            // if the POM configured no reports, give all from plugin
            if ( reportSet == null || reportSet.getReports().contains( mojoDescriptor.getGoal() ) )
            {
                try
                {
                    String id = null;
                    if ( reportSet != null )
                    {
                        id = reportSet.getId();
                    }
                    MojoExecution mojoExecution = new MojoExecution( mojoDescriptor, id );

                    String executionId = mojoExecution.getExecutionId();
                    Xpp3Dom dom = project.getReportConfiguration( reportPlugin.getGroupId(), reportPlugin.getArtifactId(), executionId );

                    reports.add( getConfiguredMojo( mojoDescriptor, session, dom, project ) );
                }
                catch ( ComponentLookupException e )
                {
                    throw new PluginManagerException( "Error looking up plugin: ", e );
                }
            }
        }
        return reports;
    }

    private PlexusContainer getPluginContainer( PluginDescriptor pluginDescriptor )
        throws PluginManagerException
    {
        String pluginKey = pluginDescriptor.getPluginLookupKey();

        PlexusContainer pluginContainer = container.getChildContainer( pluginKey );

        if ( pluginContainer == null )
        {
            throw new PluginManagerException( "Cannot find PlexusContainer for plugin: " + pluginKey );
        }
        return pluginContainer;
    }

    private Mojo getConfiguredMojo( MojoDescriptor mojoDescriptor, MavenSession session, Xpp3Dom dom,
                                    MavenProject project )
        throws ComponentLookupException, PluginConfigurationException, PluginManagerException
    {
        PlexusContainer pluginContainer = getPluginContainer( mojoDescriptor.getPluginDescriptor() );

        PluginDescriptor pluginDescriptor = mojoDescriptor.getPluginDescriptor();

        // if this is the first time this plugin has been used, the plugin's container will only
        // contain the plugin's artifact in isolation; we need to finish resolving the plugin's
        // dependencies, and add them to the container.
        ensurePluginContainerIsComplete( pluginDescriptor, pluginContainer, project, session );

        Mojo plugin = (Mojo) pluginContainer.lookup( Mojo.ROLE, mojoDescriptor.getRoleHint() );

        plugin.setLog( mojoLogger );

        PlexusConfiguration pomConfiguration;
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

        PlexusConfiguration mergedConfiguration = mergeConfiguration( pomConfiguration, mojoDescriptor
            .getMojoConfiguration() );

        // TODO: plexus
        //            PlexusConfiguration mergedConfiguration = mergeConfiguration( pomConfiguration,
        //                                                                          mojoDescriptor.getConfiguration() );

        ExpressionEvaluator expressionEvaluator = new PluginParameterExpressionEvaluator( session, pluginDescriptor,
                                                                                          pathTranslator, getLogger(),
                                                                                          project );

        PlexusConfiguration extractedMojoConfiguration = extractMojoConfiguration( mergedConfiguration,
                                                                                   mojoDescriptor );

        checkRequiredParameters( mojoDescriptor, extractedMojoConfiguration, expressionEvaluator, plugin );

        populatePluginFields( plugin, mojoDescriptor, extractedMojoConfiguration, pluginContainer,
                              expressionEvaluator );
        return plugin;
    }

    private void ensurePluginContainerIsComplete( PluginDescriptor pluginDescriptor, PlexusContainer pluginContainer,
                                                  MavenProject project, MavenSession session )
        throws PluginConfigurationException
    {
        // if the plugin's already been used once, don't re-do this step...
        // otherwise, we have to finish resolving the plugin's classpath and start the container.
        if ( pluginDescriptor.getArtifacts() != null && pluginDescriptor.getArtifacts().size() == 1 )
        {
            // TODO: this is a little shady...
            Artifact pluginArtifact = (Artifact) pluginDescriptor.getArtifacts().get( 0 );

            try
            {
                MavenMetadataSource metadataSource = new MavenMetadataSource( artifactResolver, mavenProjectBuilder,
                                                                              artifactFactory );
    
                List remoteRepositories = new ArrayList();
                
                remoteRepositories.addAll( project.getRemoteArtifactRepositories() );
                remoteRepositories.addAll( project.getPluginArtifactRepositories() );
                
                ArtifactRepository localRepository = session.getLocalRepository();
                
                ResolutionGroup resolutionGroup = metadataSource.retrieve( pluginArtifact, localRepository,
                                                            project.getPluginArtifactRepositories() );
                
                Set dependencies = resolutionGroup.getArtifacts();
    
                ArtifactResolutionResult result = artifactResolver.resolveTransitively( dependencies, pluginArtifact,
                                                                                        localRepository,
                                                                                        remoteRepositories,
                                                                                        metadataSource,
                                                                                        artifactFilter );
    
                Set resolved = result.getArtifacts();
    
                for ( Iterator it = resolved.iterator(); it.hasNext(); )
                {
                    Artifact artifact = (Artifact) it.next();
    
                    if ( artifact != pluginArtifact )
                    {
                        pluginContainer.addJarResource( artifact.getFile() );
                    }
                }
    
                pluginDescriptor.setClassRealm( pluginContainer.getContainerRealm() );
    
                // TODO: this is probably overkill as it is rarely used - can we use a mojo tag to signal this will be
                // used or check its configuration? Also, when it is used, perhaps it is more effecient to resolve
                // everything at once and apply the exclusion filter when constructing the plugin container above.
                // Check this out with yourkit
                ArtifactFilter distroProvidedFilter = new InversionArtifactFilter( artifactFilter );
    
                ArtifactResolutionResult distroProvidedResult = artifactResolver
                    .resolveTransitively( dependencies, pluginArtifact, localRepository, remoteRepositories,
                                          metadataSource, distroProvidedFilter );
    
                Set distroProvided = distroProvidedResult.getArtifacts();
    
                List unfilteredArtifactList = new ArrayList( resolved.size() + distroProvided.size() );
    
                unfilteredArtifactList.addAll( resolved );
                unfilteredArtifactList.addAll( distroProvided );
    
                pluginDescriptor.setArtifacts( unfilteredArtifactList );
            }
            catch ( ArtifactResolutionException e )
            {
                throw new PluginConfigurationException( "Cannot resolve plugin dependencies", e );
            }
            catch ( PlexusContainerException e )
            {
                throw new PluginConfigurationException( "Cannot start plugin container", e );
            }
            catch ( ArtifactMetadataRetrievalException e )
            {
                throw new PluginConfigurationException( "Cannot resolve plugin dependencies", e );
            }
        }
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
                extractedConfiguration.addChild( DefaultPluginManager.copyConfiguration( child ) );
            }
            else
            {
                // TODO: I defy anyone to find these messages in the '-X' output! Do we need a new log level?
                // ideally, this would be elevated above the true debug output, but below the default INFO level...
                getLogger().debug(
                    "*** WARNING: Configuration \'" + child.getName() + "\' is not used in goal \'" +
                        mojoDescriptor.getFullGoalName() + "; this may indicate a typo... ***" );
            }
        }

        return extractedConfiguration;
    }

    private void checkRequiredParameters( MojoDescriptor goal, PlexusConfiguration configuration,
                                          ExpressionEvaluator expressionEvaluator, Mojo plugin )
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

                    if ( fieldValue == null && StringUtils.isNotEmpty( parameter.getAlias() ) )
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
                    throw new PluginConfigurationException( "Bad expression", e );
                }

                if ( fieldValue == null && goal.getComponentConfigurator() == null )
                {
                    try
                    {
                        // TODO: remove in beta-1
                        Field field = findPluginField( plugin.getClass(), parameter.getName() );
                        boolean accessible = field.isAccessible();
                        if ( !accessible )
                        {
                            field.setAccessible( true );
                        }
                        fieldValue = field.get( plugin );
                        if ( !accessible )
                        {
                            field.setAccessible( false );
                        }
                        if ( fieldValue != null )
                        {
                            getLogger().warn(
                                "DEPRECATED: using default-value to set the default value of field '" +
                                    parameter.getName() + "'" );
                        }
                    }
                    catch ( NoSuchFieldException e )
                    {
                        throw new PluginConfigurationException( "Unable to find field to check default value", e );
                    }
                    catch ( IllegalAccessException e )
                    {
                        throw new PluginConfigurationException( "Unable to read field to check default value", e );
                    }
                }

                if ( fieldValue == null )
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

    private void validatePomConfiguration( MojoDescriptor goal, PlexusConfiguration pomConfiguration )
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

            if ( value == null && StringUtils.isNotEmpty( parameter.getAlias() ) )
            {
                key = parameter.getAlias();
                value = pomConfiguration.getChild( key, false );
            }

            if ( value != null )
            {
                // Make sure the parameter is either editable/configurable, or else is NOT specified in the POM
                if ( !parameter.isEditable() )
                {
                    StringBuffer errorMessage = new StringBuffer()
                        .append( "ERROR: Cannot override read-only parameter: " );
                    errorMessage.append( key );
                    errorMessage.append( " in goal: " ).append( goal.getFullGoalName() );

                    throw new PluginConfigurationException( errorMessage.toString() );
                }

                String deprecated = parameter.getDeprecated();
                if ( StringUtils.isNotEmpty( deprecated ) )
                {
                    getLogger().warn( "DEPRECATED [" + parameter.getName() + "]: " + deprecated );
                }
            }
        }
    }

    private PlexusConfiguration mergeConfiguration( PlexusConfiguration dominant, PlexusConfiguration configuration )
    {
        // TODO: share with mergeXpp3Dom
        PlexusConfiguration[] children = configuration.getChildren();
        for ( int i = 0; i < children.length; i++ )
        {
            PlexusConfiguration child = children[i];
            PlexusConfiguration childDom = (XmlPlexusConfiguration) dominant.getChild( child.getName(), false );
            if ( childDom != null )
            {
                mergeConfiguration( childDom, child );
            }
            else
            {
                dominant.addChild( copyConfiguration( child ) );
            }
        }
        return dominant;
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

    private void populatePluginFields( Mojo plugin, MojoDescriptor mojoDescriptor, PlexusConfiguration configuration,
                                       PlexusContainer pluginContainer, ExpressionEvaluator expressionEvaluator )
        throws PluginConfigurationException
    {
        ComponentConfigurator configurator = null;

        try
        {
            String configuratorId = mojoDescriptor.getComponentConfigurator();

            // TODO: should this be known to the component factory instead? And if so, should configuration be part of lookup?
            // [jc]: I don't think we can be that strict with the configurator. It makes some measure of sense that
            // people may want different configurators for their java mojos...
            if ( StringUtils.isNotEmpty( configuratorId ) )
            {
                configurator = (ComponentConfigurator) pluginContainer.lookup( ComponentConfigurator.ROLE,
                                                                               configuratorId );
            }
            else
            {
                configurator = (ComponentConfigurator) pluginContainer.lookup( ComponentConfigurator.ROLE );
            }

            configurator.configureComponent( plugin, configuration, expressionEvaluator, pluginContainer
                .getContainerRealm() );
        }
        catch ( ComponentConfigurationException e )
        {
            throw new PluginConfigurationException( "Unable to parse the created DOM for plugin configuration", e );
        }
        catch ( ComponentLookupException e )
        {
            throw new PluginConfigurationException(
                "Unable to retrieve component configurator for plugin configuration", e );
        }
        finally
        {
            if ( configurator != null )
            {
                try
                {
                    pluginContainer.release( configurator );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().debug( "Failed to release plugin container - ignoring." );
                }
            }
        }
    }

    private Field findPluginField( Class clazz, String key )
        throws NoSuchFieldException
    {
        try
        {
            return clazz.getDeclaredField( key );
        }
        catch ( NoSuchFieldException e )
        {
            Class superclass = clazz.getSuperclass();
            if ( superclass != Object.class )
            {
                return findPluginField( superclass, key );
            }
            else
            {
                throw e;
            }
        }
    }

    public static String createPluginParameterRequiredMessage( MojoDescriptor mojo, Parameter parameter,
                                                               String expression )
    {
        StringBuffer message = new StringBuffer();

        message.append( "The '" + parameter.getName() );
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
    // Lifecycle
    // ----------------------------------------------------------------------

    public void contextualize( Context context )
        throws ContextException
    {
        container = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );

        LoggerManager manager = null;

        try
        {
            manager = (LoggerManager) container.lookup( LoggerManager.ROLE );

            mojoLogger = new DefaultLog( manager.getLoggerForComponent( Mojo.ROLE ) );
        }
        catch ( ComponentLookupException e )
        {
            throw new ContextException( "Error locating a logger manager", e );
        }
        finally
        {
            if ( manager != null )
            {
                try
                {
                    container.release( manager );
                }
                catch ( ComponentLifecycleException e )
                {
                    getLogger().error( "Error releasing the logger manager - ignoring", e );
                }
            }
        }
    }

    public void initialize()
    {
        // TODO: configure this from bootstrap or scan lib
        Set artifacts = new HashSet();
        artifacts.add( "classworlds" );
        artifacts.add( "commons-cli" );
        artifacts.add( "commons-validator" );
        artifacts.add( "jline" );
        artifacts.add( "jsch" );
        artifacts.add( "maven-artifact" );
        artifacts.add( "maven-artifact-manager" );
        artifacts.add( "maven-core" );
        artifacts.add( "maven-model" );
        artifacts.add( "maven-monitor" );
        artifacts.add( "maven-plugin-api" );
        artifacts.add( "maven-plugin-descriptor" );
        artifacts.add( "maven-project" );
        artifacts.add( "maven-settings" );
        artifacts.add( "oro" );
        artifacts.add( "plexus-container-default" );
        artifacts.add( "plexus-input-handler" );
        artifacts.add( "plexus-utils" );
        artifacts.add( "wagon-provider-api" );
        artifacts.add( "wagon-file" );
        artifacts.add( "wagon-http-lightweight" );
        artifacts.add( "wagon-ssh" );
        // TODO: remove doxia
        artifacts.add( "doxia-core" );
        artifacts.add( "maven-reporting-api" );
        artifactFilter = new ExclusionSetFilter( artifacts );
    }

    // ----------------------------------------------------------------------
    // Artifact resolution
    // ----------------------------------------------------------------------

    private void resolveTransitiveDependencies( MavenSession context, ArtifactResolver artifactResolver,
                                                MavenProjectBuilder mavenProjectBuilder, String scope,
                                                ArtifactFactory artifactFactory, MavenProject project )
        throws ArtifactResolutionException
    {
        MavenMetadataSource sourceReader = new MavenMetadataSource( artifactResolver, mavenProjectBuilder,
                                                                    artifactFactory );

        ArtifactFilter filter = new ScopeArtifactFilter( scope );

        // TODO: such a call in MavenMetadataSource too - packaging not really the intention of type
        Artifact artifact = artifactFactory.createArtifact( project.getGroupId(), project.getArtifactId(),
                                                            project.getVersion(), null, project.getPackaging() );

        // TODO: we don't need to resolve over and over again, as long as we are sure that the parameters are the same
        // check this with yourkit as a hot spot.
        ArtifactResolutionResult result = artifactResolver.resolveTransitively( project.getDependencyArtifacts(),
                                                                                artifact, context.getLocalRepository(),
                                                                                project.getRemoteArtifactRepositories(),
                                                                                sourceReader, filter );

        project.setArtifacts( result.getArtifacts() );
    }

    // ----------------------------------------------------------------------
    // Artifact downloading
    // ----------------------------------------------------------------------

    private void downloadDependencies( MavenProject project, MavenSession context, ArtifactResolver artifactResolver )
        throws ArtifactResolutionException
    {
        ArtifactRepository localRepository = context.getLocalRepository();
        List remoteArtifactRepositories = project.getRemoteArtifactRepositories();

        for ( Iterator it = project.getArtifacts().iterator(); it.hasNext(); )
        {
            Artifact artifact = (Artifact) it.next();

            artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );
        }
    }

    public Object getPluginComponent( Plugin plugin, String role, String roleHint )
        throws ComponentLookupException, PluginManagerException
    {
        PluginDescriptor pluginDescriptor = pluginCollector.getPluginDescriptor( plugin );

        PlexusContainer pluginContainer = getPluginContainer( pluginDescriptor );

        return pluginContainer.lookup( role, roleHint );
    }

}
