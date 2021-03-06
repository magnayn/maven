package org.apache.maven.project.builder;

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

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.maven.shared.model.*;
import org.apache.maven.shared.model.impl.DefaultModelDataSource;
import org.apache.maven.project.builder.rules.*;
import org.apache.maven.project.builder.factories.PluginExecutionIdModelContainerFactory;
import org.apache.maven.project.builder.factories.AlwaysJoinModelContainerFactory;
import org.apache.maven.project.builder.factories.ArtifactModelContainerFactory;
import org.apache.maven.project.builder.factories.IdModelContainerFactory;

/**
 * Provides methods for transforming model properties into a domain model for the pom classic format and vice versa.
 */
public class PomTransformer
    implements ModelTransformer
{

    private final DomainModelFactory factory;

    public PomTransformer(DomainModelFactory factory)
    {
        this.factory = factory;
    }

    public static final List<ModelContainerFactory> MODEL_CONTAINER_FACTORIES = 
    	Collections.unmodifiableList(Arrays.asList(new ArtifactModelContainerFactory(), 
    			new IdModelContainerFactory(ProjectUri.PluginRepositories.PluginRepository.xUri),
    			new IdModelContainerFactory(ProjectUri.Repositories.Repository.xUri),
    			new IdModelContainerFactory(ProjectUri.Reporting.Plugins.Plugin.ReportSets.ReportSet.xUri),
    			new IdModelContainerFactory(ProjectUri.Profiles.Profile.xUri),
    			new IdModelContainerFactory(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri)));

    private static Collection<ModelContainerInfo> goals_infos = Arrays.asList(
             ModelContainerInfo.Factory.createModelContainerInfo(
                    new AlwaysJoinModelContainerFactory(), new ExecutionRule(), null)
            );

    private static Collection<ModelContainerInfo> plugin_executions = Arrays.asList(
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new IdModelContainerFactory(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri),
                     null, goals_infos)
            );

    private static Collection<ModelContainerInfo> dependency_exclusions = Arrays.asList(
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new IdModelContainerFactory(ProjectUri.DependencyManagement.Dependencies.Dependency.Exclusions.Exclusion.xUri),
                     new DependencyRule(), null)
            );

    //Don't add subcontainers here, breaks MNG-3821
    public static final Collection<ModelContainerInfo> MODEL_CONTAINER_INFOS = Arrays.asList(
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new ArtifactModelContainerFactory(), null, plugin_executions),
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new IdModelContainerFactory(ProjectUri.PluginRepositories.PluginRepository.xUri), null, null),
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new IdModelContainerFactory(ProjectUri.Repositories.Repository.xUri), null, null),
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new ArtifactModelContainerFactory(), null, dependency_exclusions),
            ModelContainerInfo.Factory.createModelContainerInfo(
                    new IdModelContainerFactory(ProjectUri.Profiles.Profile.xUri), null, null)
    );

    /**
     * The URIs this transformer supports
     */
    public static final Set<String> URIS = Collections.unmodifiableSet(new HashSet<String>( Arrays.asList(  ProjectUri.Build.Extensions.xUri,
                                                                          ProjectUri.Build.PluginManagement.Plugins.xUri,
                                                                          ProjectUri.Build.PluginManagement.Plugins.Plugin.configuration,
                                                                          ProjectUri.Build.PluginManagement.Plugins.Plugin.Executions.xUri,
                                                                          ProjectUri.Build.PluginManagement.Plugins.Plugin.Executions.Execution.Goals.xURI,
                                                                          ProjectUri.Build.PluginManagement.Plugins.Plugin.Dependencies.xUri,
                                                                          ProjectUri.Build.PluginManagement.Plugins.Plugin.Dependencies.Dependency.Exclusions.xUri,
                                                                          ProjectUri.Build.Plugins.xUri,
                                                                          ProjectUri.properties,
                                                                          ProjectUri.Build.Plugins.Plugin.configuration,
                                                                          ProjectUri.Reporting.Plugins.xUri,
                                                                          ProjectUri.Reporting.Plugins.Plugin.configuration,
                                                                          ProjectUri.Build.Plugins.Plugin.Dependencies.xUri,
                                                                          ProjectUri.Build.Resources.xUri,
                                                                          ProjectUri.Build.Resources.Resource.includes,
                                                                          ProjectUri.Build.Resources.Resource.excludes,
                                                                          ProjectUri.Build.TestResources.xUri,
                                                                          ProjectUri.Build.Filters.xUri,
                                                                          ProjectUri.CiManagement.Notifiers.xUri,
                                                                          ProjectUri.Contributors.xUri,
                                                                          ProjectUri.Dependencies.xUri,
                                                                          ProjectUri.DependencyManagement.Dependencies.xUri,
                                                                          ProjectUri.Developers.xUri,
                                                                          ProjectUri.Developers.Developer.roles,
                                                                          ProjectUri.Licenses.xUri,
                                                                          ProjectUri.MailingLists.xUri,
                                                                          ProjectUri.Modules.xUri,
                                                                          ProjectUri.PluginRepositories.xUri,
                                                                          ProjectUri.Profiles.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.Plugins.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.Plugins.Plugin.Dependencies.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.Plugins.Plugin.Executions.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.Resources.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.TestResources.xUri,
                                                                          ProjectUri.Profiles.Profile.Dependencies.xUri,
                                                                          ProjectUri.Profiles.Profile.DependencyManagement.Dependencies.xUri,
                                                                          ProjectUri.Profiles.Profile.PluginRepositories.xUri,
                                                                          ProjectUri.Profiles.Profile.Reporting.Plugins.xUri,
                                                                          ProjectUri.Profiles.Profile.Repositories.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.PluginManagement.Plugins.xUri,
                                                                          ProjectUri.Profiles.Profile.Build.PluginManagement.Plugins.Plugin.Dependencies.xUri,
                                                                          ProjectUri.Reporting.Plugins.xUri,
                                                                          ProjectUri.Repositories.xUri) ));

    /**
     * The URIs that denote file/directory paths and need their file separators being normalized.
     */
    private static final Set<String> PATH_URIS =
        Collections.unmodifiableSet( new HashSet<String>(
                                                          Arrays.asList(
                                                                         ProjectUri.Build.directory,
                                                                         ProjectUri.Build.outputDirectory,
                                                                         ProjectUri.Build.testOutputDirectory,
                                                                         ProjectUri.Build.sourceDirectory,
                                                                         ProjectUri.Build.testSourceDirectory,
                                                                         ProjectUri.Build.scriptSourceDirectory,
                                                                         ProjectUri.Build.Resources.Resource.directory,
                                                                         ProjectUri.Build.TestResources.TestResource.directory,
                                                                         ProjectUri.Build.Filters.filter,
                                                                         ProjectUri.Reporting.outputDirectory ) ) );   
    /**
     * @see ModelTransformer#transformToDomainModel(java.util.List, java.util.List)
     */
    public final DomainModel transformToDomainModel( List<ModelProperty> properties,  List<? extends ModelEventListener> eventListeners )
        throws IOException
    {
        if ( properties == null )
        {
            throw new IllegalArgumentException( "properties: null" );
        }

        if( eventListeners == null )
        {
            eventListeners = new ArrayList<ModelEventListener>();
        }
        else
        {
            eventListeners = new ArrayList<ModelEventListener>(eventListeners);
        }

        List<ModelProperty> props = new ArrayList<ModelProperty>( properties );


        ModelDataSource source = new DefaultModelDataSource( props, PomTransformer.MODEL_CONTAINER_FACTORIES );

        //Dependency Management
        new DependencyManagementDataSourceRule().execute( source );

        //Plugin Management
        List<ModelProperty> joinedContainers = new ArrayList<ModelProperty>();
        for ( ModelContainer pluginContainer : source.queryFor( ProjectUri.Build.Plugins.Plugin.xUri ) )
        {
            for ( ModelContainer managementContainer : source.queryFor( ProjectUri.Build.PluginManagement.Plugins.Plugin.xUri ) )
            {
                //Transform from plugin management to plugins
                List<ModelProperty> transformedProperties = new ArrayList<ModelProperty>();
                for ( ModelProperty mp : managementContainer.getProperties() )
                {
                    if ( mp.getUri().startsWith( ProjectUri.Build.PluginManagement.xUri ) )
                    {
                        transformedProperties.add( new ModelProperty( mp.getUri().replace( ProjectUri.Build.PluginManagement.xUri,
                                ProjectUri.Build.xUri ), mp.getResolvedValue() ) );
                    }
                }
                
                managementContainer = new ArtifactModelContainerFactory().create( transformedProperties );

                //Remove duplicate executions tags
                boolean hasExecutionsTag = false;
                for ( ModelProperty mp : pluginContainer.getProperties() )
                {
                    if ( mp.getUri().equals( ProjectUri.Build.Plugins.Plugin.Executions.xUri ) )
                    {
                        hasExecutionsTag = true;
                        break;
                    }
                }
                
                List<ModelProperty> pList;
                
                if ( !hasExecutionsTag )
                {
                    pList = managementContainer.getProperties();
                }
                else
                {
                    pList = new ArrayList<ModelProperty>();
                    
                    for ( ModelProperty mp : managementContainer.getProperties() )
                    {
                        if ( !mp.getUri().equals( ProjectUri.Build.Plugins.Plugin.Executions.xUri ) )
                        {
                            pList.add( mp );
                        }
                    }
                }
                
                managementContainer = new ArtifactModelContainerFactory().create( pList );

                ModelContainerAction action = pluginContainer.containerAction( managementContainer );

                //Join Execution Containers
                if ( action.equals( ModelContainerAction.JOIN ) || action.equals( ModelContainerAction.DELETE ) )
                {
                    ModelDataSource pluginDatasource = new DefaultModelDataSource(  pluginContainer.getProperties(), PomTransformer.MODEL_CONTAINER_FACTORIES );
                    ModelDataSource managementDatasource = new DefaultModelDataSource( managementContainer.getProperties(), PomTransformer.MODEL_CONTAINER_FACTORIES );

                    List<ModelContainer> managementExecutionContainers = managementDatasource.queryFor(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri);
                    List<ModelProperty> managementPropertiesWithoutExecutions = new ArrayList<ModelProperty>(managementContainer.getProperties());
                    for(ModelContainer a : managementExecutionContainers)
                    {
                        managementPropertiesWithoutExecutions.removeAll(a.getProperties());
                    }

                    source.joinWithOriginalOrder( pluginContainer, new ArtifactModelContainerFactory().create(managementPropertiesWithoutExecutions) );

                    List<ModelContainer> pluginExecutionContainers = pluginDatasource.queryFor(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri);
                    List<ModelContainer> joinedExecutionContainers = new ArrayList<ModelContainer>();
                 
                    for(ModelContainer a : managementExecutionContainers)
                    {
                    	boolean hasId = false;
                    	for(ModelProperty mp : a.getProperties()) {
                    		if(mp.getUri().equals(ProjectUri.Build.Plugins.Plugin.Executions.Execution.id)) {
                    			hasId = true;
                    			break;
                    		}
                    	}
                    	
                    	ModelContainer c = a;
                    	if(!hasId) {
                    		List<ModelProperty> listWithId = new ArrayList<ModelProperty>(a.getProperties());
                    		listWithId.add(1, new ModelProperty(ProjectUri.Build.Plugins.Plugin.Executions.Execution.id, "default"));
                    		c = new IdModelContainerFactory(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri).create(listWithId);
                    	}
                    	
                    	
                        for(ModelContainer b : pluginExecutionContainers)
                        {
                            if(b.containerAction(c).equals(ModelContainerAction.JOIN))
                            {
                                //MNG-3995 - property lost here
                                joinedContainers.addAll(source.join(b, c).getProperties());
                                joinedExecutionContainers.add(a);
                            }
                        }
                    }

                    ModelProperty executionsProperty = null;
                    for(ModelProperty a : pluginContainer.getProperties())
                    {
                        if(a.getUri().equals(ProjectUri.Build.Plugins.Plugin.Executions.xUri)) {
                            executionsProperty = a;
                            break;
                        }
                    }

                    if(executionsProperty == null)
                    {
                        for(ModelProperty a : managementPropertiesWithoutExecutions)
                        {
                            if(a.getUri().equals(ProjectUri.Build.Plugins.Plugin.Executions.xUri)) {
                                executionsProperty = a;
                                break;
                            }
                        }
                    }

                    if(executionsProperty != null)
                    {
                        managementExecutionContainers.removeAll(joinedExecutionContainers);
                        Collections.reverse(managementExecutionContainers);
                        for(ModelContainer a : managementExecutionContainers)
                        {
                            source.insertModelPropertiesAfter(executionsProperty,
                                    ModelTransformerContext.sort(a.getProperties(), ProjectUri.Build.Plugins.Plugin.Executions.xUri));
                        }
                    }
                    
                }
            }
        }

        props = source.getModelProperties();

        //TransformerRule: Do not join plugin executions without ids
        Set<ModelProperty> removeProperties = new HashSet<ModelProperty>();
        
        ModelDataSource dataSource = new DefaultModelDataSource( props, PomTransformer.MODEL_CONTAINER_FACTORIES );

        List<ModelContainer> containers = dataSource.queryFor( ProjectUri.Build.Plugins.Plugin.xUri );
        
        for ( ModelContainer pluginContainer : containers )
        {
            ModelDataSource executionSource = new DefaultModelDataSource( pluginContainer.getProperties(),
                                  Arrays.asList( new ArtifactModelContainerFactory(), new PluginExecutionIdModelContainerFactory() ));
            
            List<ModelContainer> executionContainers = executionSource.queryFor( ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri );
            
            if ( executionContainers.size() < 2 )
            {
                continue;
            }

            boolean hasAtLeastOneWithoutId = false;
            
            for ( ModelContainer executionContainer : executionContainers )
            {

                
                if ( hasAtLeastOneWithoutId && !hasExecutionId( executionContainer ) && executionContainers.indexOf( executionContainer ) > 0 )
                {
                    removeProperties.addAll( executionContainer.getProperties() );
                }
                if ( !hasAtLeastOneWithoutId )
                {
                    hasAtLeastOneWithoutId = !hasExecutionId( executionContainer );
                }                
            }
        }
        
        props.removeAll( removeProperties );

        //Execution TransformerRule - extension for this needs to be pushed into model-builder
        dataSource = new DefaultModelDataSource( props, PomTransformer.MODEL_CONTAINER_FACTORIES );

        for(ModelContainer mc : dataSource.queryFor( ProjectUri.Build.Plugins.Plugin.xUri ))
        {
            ModelDataSource executionSource =
                    new DefaultModelDataSource(mc.getProperties(),
                            Arrays.asList(new IdModelContainerFactory(ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri),
                                    new AlwaysJoinModelContainerFactory()));
            for(ModelContainer es : executionSource.queryFor( ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri )) {
                ExecutionRule rule = new ExecutionRule();
               // List<ModelProperty> x = rule.execute(es.getProperties());
                List<ModelProperty> x = !aContainsAnyOfB(es.getProperties(), joinedContainers) ? rule.execute(es.getProperties()) :
                        ModelTransformerContext.sort(rule.execute(es.getProperties()),
                                ProjectUri.Build.Plugins.Plugin.Executions.Execution.xUri);
                
                dataSource.replace(es, es.createNewInstance(x));
            }
        }

        props =// false ? ModelTransformerContext.sort(dataSource.getModelProperties(), ProjectUri.baseUri)
                dataSource.getModelProperties();
       
        for(ModelEventListener listener : eventListeners)
        {
            ModelDataSource ds = new DefaultModelDataSource( props, listener.getModelContainerFactories() );
            for(String uri : listener.getUris() )
            {
                listener.fire(ds.queryFor(uri));
            }
        }
        
        //Cleanup props (MNG-3979)
        List<ModelProperty> p = new ArrayList<ModelProperty>();
        for(ModelProperty mp : props)
        {
            if(mp.getResolvedValue() != null
                    && mp.getResolvedValue().trim().equals(""))
            {
                int index = props.indexOf(mp) + 1;

                if(index < props.size() && mp.isParentOf(props.get(index)) && mp.getDepth() != props.get(index).getDepth()
                        && !props.get(index).getUri().contains("#property"))
                {
                    p.add(new ModelProperty(mp.getUri(), null));
                }
                else
                {
                    p.add(mp);
                }
            }
            else if ( mp.getResolvedValue() != null && PATH_URIS.contains( mp.getUri() ) )
            {
                // normalize file separator
                p.add( new ModelProperty( mp.getUri(), new File( mp.getResolvedValue() ).getPath() ) );
            }
            else
            {
                p.add(mp);
            }
        }

        return factory.createDomainModel( p );
    }

    private static boolean aContainsAnyOfB(List<ModelProperty> a, List<ModelProperty> b) {
        for(ModelProperty mpA : a )
        {
            for(ModelProperty mpB : b)
            {
                if(mpA.equals(mpB))
                {
                    return true;
                }
            }
        }
        return false;
    }



    List<TransformerRule> transformerRules = Arrays.asList(new MissingVersionTransformerRule(),
            new DefaultDependencyScopeTransformerRule(), new MissingGroupIdTransformerRule());

    List<TransformerRemovalRule> transformerRemovalRules = Arrays.asList(new DefaultExecutionIdTransformerRule(),
            new ModulesNotInheritedTransformerRule(), new NotInheritedPluginExecutionTransformerRule(),
            new NotInheritedPluginTransformerRule(), new RelativePathNotInheritedTransformerRule(),
            new PackagingNotInheritedTransformerRule(), new NameNotInheritedTransformerRule());
    
    /**
     * @see ModelTransformer#transformToModelProperties(java.util.List)
     */
    public final List<ModelProperty> transformToModelProperties(List<? extends DomainModel> domainModels
    )
        throws IOException
    {
        if ( domainModels == null || domainModels.isEmpty() )
        {
            throw new IllegalArgumentException( "domainModels: null or empty" );
        }

        List<ModelProperty> modelProperties = new ArrayList<ModelProperty>();
        List<String> projectNames = new ArrayList<String>();
        StringBuilder projectUrl = new StringBuilder( 128 );
        StringBuilder siteUrl = new StringBuilder( 128 );
        StringBuilder scmUrl = new StringBuilder( 128 );
        StringBuilder scmConnectionUrl = new StringBuilder( 128 );
        StringBuilder scmDeveloperUrl = new StringBuilder( 128 );

        boolean containsBuildResources = false;
        boolean containsTestResources = false;
        boolean containsPluginRepositories = false;
        boolean containsLicenses = false;
        boolean containsDevelopers = false;
        boolean containsContributors = false;
        boolean containsMailingLists = false;
        boolean containsOrganization = false;
        boolean containsIssueManagement = false;
        boolean containsCiManagement = false;
        boolean containsDistRepo = false;
        boolean containsDistSnapRepo = false;
        boolean containsDistSite = false;


        for ( DomainModel domainModel : domainModels )
        {
            List<ModelProperty> tmp = domainModel.getModelProperties();

            List<ModelProperty> clearedProperties = new ArrayList<ModelProperty>();

            for(TransformerRule rule : transformerRules)
            {
                rule.execute(tmp, domainModel.isMostSpecialized());
            }

            for(TransformerRemovalRule rule : transformerRemovalRules)
            {
                tmp.removeAll(rule.executeWithReturnPropertiesToRemove(tmp, domainModel.isMostSpecialized()));
            }

            // Project URL TransformerRule
            adjustUrl( projectUrl, tmp, ProjectUri.url, projectNames );
            // Site TransformerRule
            adjustUrl( siteUrl, tmp, ProjectUri.DistributionManagement.Site.url, projectNames );
            // SCM TransformerRule
            adjustUrl( scmUrl, tmp, ProjectUri.Scm.url, projectNames );
            // SCM Connection TransformerRule
            adjustUrl( scmConnectionUrl, tmp, ProjectUri.Scm.connection, projectNames );
            // SCM Developer TransformerRule
            adjustUrl( scmDeveloperUrl, tmp, ProjectUri.Scm.developerConnection, projectNames );

            // Profiles TransformerRule: not inherited
            // Prerequisites TransformerRule: not inherited
            // DistributionManagent.Relocation TransformerRule: not inherited
            if ( !domainModel.isMostSpecialized() )
            {
                for ( ModelProperty mp : tmp )
                {
                    String uri = mp.getUri();
                    if ( uri.startsWith( ProjectUri.Profiles.xUri )
                        || uri.startsWith( ProjectUri.Prerequisites.xUri )
                        || uri.startsWith( ProjectUri.DistributionManagement.Relocation.xUri ) )
                    {
                        clearedProperties.add( mp );
                    }
                }
            }

            // Remove Plugin Repository Inheritance TransformerRule
            // License TransformerRule: only inherited if not specified in child
            // Organization TransformerRule: only inherited if not specified in child
            // Developers TransformerRule: only inherited if not specified in child
            // Contributors TransformerRule: only inherited if not specified in child
            // Mailing Lists TransformerRule: only inherited if not specified in child
            // Build Resources TransformerRule: only inherited if not specified in child
            // Build Test Resources TransformerRule: only inherited if not specified in child
            // CI Management TransformerRule: only inherited if not specified in child
            // Issue Management TransformerRule: only inherited if not specified in child
            // Distribution Management Repository TransformerRule: only inherited if not specified in child
            // Distribution Management Snapshot Repository TransformerRule: only inherited if not specified in child
            // Distribution Management Site TransformerRule: only inherited if not specified in child
            for ( ModelProperty mp : tmp )
            {
                String uri = mp.getUri();
                if ( ( containsBuildResources && uri.startsWith( ProjectUri.Build.Resources.xUri ) )
                    || ( containsTestResources && uri.startsWith( ProjectUri.Build.TestResources.xUri ) )
                    || ( containsPluginRepositories && uri.startsWith( ProjectUri.PluginRepositories.xUri ) )
                    || ( containsOrganization && uri.startsWith( ProjectUri.Organization.xUri ) )
                    || ( containsLicenses && uri.startsWith( ProjectUri.Licenses.xUri ) )
                    || ( containsDevelopers && uri.startsWith( ProjectUri.Developers.xUri ) )
                    || ( containsContributors && uri.startsWith( ProjectUri.Contributors.xUri ) )
                    || ( containsMailingLists && uri.startsWith( ProjectUri.MailingLists.xUri ) )
                    || ( containsCiManagement && uri.startsWith( ProjectUri.CiManagement.xUri ) )
                    || ( containsIssueManagement && uri.startsWith( ProjectUri.IssueManagement.xUri ) )
                    || ( containsDistRepo && uri.startsWith( ProjectUri.DistributionManagement.Repository.xUri ) )
                    || ( containsDistSnapRepo && uri.startsWith( ProjectUri.DistributionManagement.SnapshotRepository.xUri ) )
                    || ( containsDistSite && uri.startsWith( ProjectUri.DistributionManagement.Site.xUri ) ) )
                {
                    clearedProperties.add( mp );
                }
            }
            containsBuildResources |= hasProjectUri( ProjectUri.Build.Resources.xUri, tmp );
            containsTestResources |= hasProjectUri( ProjectUri.Build.TestResources.xUri, tmp );
            containsPluginRepositories |= hasProjectUri( ProjectUri.PluginRepositories.xUri, tmp );
            containsOrganization |= hasProjectUri( ProjectUri.Organization.xUri, tmp );
            containsLicenses |= hasProjectUri( ProjectUri.Licenses.xUri, tmp );
            containsDevelopers |= hasProjectUri( ProjectUri.Developers.xUri, tmp );
            containsContributors |= hasProjectUri( ProjectUri.Contributors.xUri, tmp );
            containsMailingLists |= hasProjectUri( ProjectUri.MailingLists.xUri, tmp );
            containsCiManagement |= hasProjectUri( ProjectUri.CiManagement.xUri, tmp );
            containsIssueManagement |= hasProjectUri( ProjectUri.IssueManagement.xUri, tmp );
            containsDistRepo |= hasProjectUri( ProjectUri.DistributionManagement.Repository.xUri, tmp );
            containsDistSnapRepo |= hasProjectUri( ProjectUri.DistributionManagement.SnapshotRepository.xUri, tmp );
            containsDistSite |= hasProjectUri( ProjectUri.DistributionManagement.Site.xUri, tmp );

            ModelProperty artifactId = getPropertyFor( ProjectUri.artifactId, tmp );
            if ( artifactId != null )
            {
                projectNames.add( 0, artifactId.getResolvedValue() );
            }

            tmp.removeAll( clearedProperties );
            modelProperties.addAll( tmp );
            modelProperties.removeAll( clearedProperties );
        }

        //Rules processed on collapsed pom

        //TransformerRule: Remove duplicate filters
        modelProperties.removeAll(new DuplicateFiltersTransformerRule().executeWithReturnPropertiesToRemove( modelProperties , false));

        //TransformerRule: Build plugin config overrides reporting plugin config
        return new OverideConfigTransformerRule().execute( modelProperties );
    }

    /**
     * Overide this method to change the way interpolation is handled.
     *
     * @param modelProperties
     * @param interpolatorProperties
     * @param domainModel
     * @throws IOException
     */
     private static final Map<String, String> aliases = new HashMap<String, String>();

    private static void addProjectAlias( String element, boolean leaf )
    {
        String suffix = leaf ? "\\}" : "\\.";
        aliases.put( "\\$\\{project\\." + element + suffix, "\\$\\{" + element + suffix );
    }

    static
    {
        aliases.put( "\\$\\{project\\.", "\\$\\{pom\\." );
        addProjectAlias( "modelVersion", true );
        addProjectAlias( "groupId", true );
        addProjectAlias( "artifactId", true );
        addProjectAlias( "version", true );
        addProjectAlias( "packaging", true );
        addProjectAlias( "name", true );
        addProjectAlias( "description", true );
        addProjectAlias( "inceptionYear", true );
        addProjectAlias( "url", true );
        addProjectAlias( "parent", false );
        addProjectAlias( "prerequisites", false );
        addProjectAlias( "organization", false );
        addProjectAlias( "build", false );
        addProjectAlias( "reporting", false );
        addProjectAlias( "scm", false );
        addProjectAlias( "distributionManagement", false );
        addProjectAlias( "issueManagement", false );
        addProjectAlias( "ciManagement", false );
    }

    public void interpolateModelProperties( List<ModelProperty> modelProperties,
                                                   List<InterpolatorProperty> interpolatorProperties,
                                                   DomainModel domainModel )
        throws IOException
    {
        PomClassicDomainModel dm = (PomClassicDomainModel) domainModel;

        if ( !containsProjectVersion( interpolatorProperties ) )
        {
            aliases.put( "\\$\\{project.version\\}", "\\$\\{version\\}" );
        }

        List<ModelProperty> firstPassModelProperties = new ArrayList<ModelProperty>();
        List<ModelProperty> secondPassModelProperties = new ArrayList<ModelProperty>();

        ModelProperty buildProperty = new ModelProperty( ProjectUri.Build.xUri, null );
        for ( ModelProperty mp : modelProperties )
        {
            if ( mp.getValue() != null && !mp.getUri().contains( "#property" ) && !mp.getUri().contains( "#collection" ) )
            {
                if ( ( !buildProperty.isParentOf( mp ) && !mp.getUri().equals( ProjectUri.Reporting.outputDirectory ) || mp.getUri().equals( ProjectUri.Build.finalName ) ) )
                {
                    firstPassModelProperties.add( mp );
                }
                else
                {
                    secondPassModelProperties.add( mp );
                }
            }
        }

        List<InterpolatorProperty> standardInterpolatorProperties = new ArrayList<InterpolatorProperty>();
        if ( dm.isPomInBuild() )
        {
            String basedir = dm.getProjectDirectory().getAbsolutePath();
            standardInterpolatorProperties.add( new InterpolatorProperty( "${project.basedir}", basedir, PomInterpolatorTag.PROJECT_PROPERTIES.name() ) );
            standardInterpolatorProperties.add( new InterpolatorProperty( "${basedir}", basedir, PomInterpolatorTag.PROJECT_PROPERTIES.name() ) );
            standardInterpolatorProperties.add( new InterpolatorProperty( "${pom.basedir}", basedir, PomInterpolatorTag.PROJECT_PROPERTIES.name() ) );

        }

        for ( ModelProperty mp : modelProperties )
        {
            if ( mp.getUri().startsWith( ProjectUri.properties ) && mp.getValue() != null )
            {
                String uri = mp.getUri();
                standardInterpolatorProperties.add( new InterpolatorProperty( "${" + uri.substring( uri.lastIndexOf( "/" ) + 1, uri.length() ) + "}", mp.getValue(),
                                                                              PomInterpolatorTag.PROJECT_PROPERTIES.name() ) );
            }
        }

        //FIRST PASS - Withhold using build directories as interpolator properties
        List<InterpolatorProperty> ips1 = new ArrayList<InterpolatorProperty>( interpolatorProperties );
        ips1.addAll( standardInterpolatorProperties );
        ips1.addAll( ModelTransformerContext.createInterpolatorProperties( firstPassModelProperties, ProjectUri.baseUri, aliases, PomInterpolatorTag.PROJECT_PROPERTIES.name(), false, false ) );
        Collections.sort( ips1, new Comparator<InterpolatorProperty>()
        {
            public int compare( InterpolatorProperty o, InterpolatorProperty o1 )
            {
                if(o.getTag() == null || o1.getTag() == null)
                {
                    return 0;
                }
                return PomInterpolatorTag.valueOf(o.getTag()).compareTo(PomInterpolatorTag.valueOf(o1.getTag()));
            }
        });

        ModelTransformerContext.interpolateModelProperties( modelProperties, ips1 );

        //SECOND PASS - Set absolute paths on build directories
        if ( dm.isPomInBuild() )
        {
            String basedir = dm.getProjectDirectory().getAbsolutePath();
            Map<ModelProperty, ModelProperty> buildDirectories = new HashMap<ModelProperty, ModelProperty>();
            for ( ModelProperty mp : secondPassModelProperties )
            {
                if ( mp.getUri().startsWith( ProjectUri.Build.xUri ) || mp.getUri().equals( ProjectUri.Reporting.outputDirectory ) )
                {
                    File file = new File(mp.getResolvedValue());
                    if( !file.isAbsolute() && !mp.getResolvedValue().startsWith("${project.build.")
                            && !mp.getResolvedValue().equals("${project.basedir}"))
                    {
                        buildDirectories.put( mp, new ModelProperty( mp.getUri(), new File( basedir, file.getPath() ).getAbsolutePath() ) );
                    }
                }
            }

            for ( Map.Entry<ModelProperty, ModelProperty> e : buildDirectories.entrySet() )
            {
                secondPassModelProperties.remove( e.getKey() );
                secondPassModelProperties.add( e.getValue() );
            }
        }

        //THIRD PASS - Use build directories as interpolator properties
        List<InterpolatorProperty> ips2 = new ArrayList<InterpolatorProperty>( interpolatorProperties );
        ips2.addAll( standardInterpolatorProperties );
        ips2.addAll( ModelTransformerContext.createInterpolatorProperties( secondPassModelProperties, ProjectUri.baseUri, aliases, PomInterpolatorTag.PROJECT_PROPERTIES.name(), false, false ) );
        ips2.addAll( interpolatorProperties );
        Collections.sort( ips2, new Comparator<InterpolatorProperty>()
        {
            public int compare( InterpolatorProperty o, InterpolatorProperty o1 )
            {
                if(o.getTag() == null || o1.getTag() == null)
                {
                    return 0;
                }

                return PomInterpolatorTag.valueOf( o.getTag() ).compareTo( PomInterpolatorTag.valueOf( o1.getTag() ) );
            }
        } );

        ModelTransformerContext.interpolateModelProperties( modelProperties, ips2 );
    }
    /**
     * Override this method for different preprocessing of model properties.
     *
     * @param modelProperties
     * @return
     */
    public List<ModelProperty> preprocessModelProperties(List<ModelProperty> modelProperties)
    {
        return new ArrayList<ModelProperty>(modelProperties);
    }

    /**
     * Returns the base uri of all model properties: http://apache.org/maven/project/
     *
     * @return Returns the base uri of all model properties: http://apache.org/maven/project/
     */
    public final String getBaseUri()
    {
        return ProjectUri.baseUri;
    }

    /**
     * Adjusts an inherited URL to compensate for a child's relation/distance to the parent that defines the URL.
     *
     * @param url The buffer for the adjusted URL, must not be {@code null}.
     * @param properties The model properties to update, must not be {@code null}.
     * @param uri The URI of the model property defining the URL to adjust, must not be {@code null}.
     * @param ids The artifact identifiers of the parent projects, starting with the least significant parent, must not
     *            be {@code null}.
     */
    private void adjustUrl( StringBuilder url, List<ModelProperty> properties, String uri, List<String> ids )
    {
        if ( url.length() == 0 )
        {
            ModelProperty property = getPropertyFor( uri, properties );
            if ( property != null )
            {
                url.append( property.getResolvedValue() );
                for ( String id : ids )
                {
                    if ( url.length() > 0 && url.charAt( url.length() - 1 ) != '/' )
                    {
                        url.append( '/' );
                    }
                    url.append( id );
                }
                int index = properties.indexOf( property );
                properties.set( index, new ModelProperty( uri, url.toString() ) );
            }
        }
    }

    private static boolean containsProjectVersion( List<InterpolatorProperty> interpolatorProperties )
    {
        InterpolatorProperty versionInterpolatorProperty =
                new ModelProperty( ProjectUri.version, "").asInterpolatorProperty( ProjectUri.baseUri);
        for( InterpolatorProperty ip : interpolatorProperties)
        {
            if ( ip.equals( versionInterpolatorProperty ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExecutionId( ModelContainer executionContainer )
    {
        for ( ModelProperty mp : executionContainer.getProperties() )
        {
            if ( mp.getUri().equals( ProjectUri.Build.Plugins.Plugin.Executions.Execution.id ) )
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasProjectUri( String projectUri, List<ModelProperty> modelProperties )
    {
        for ( ModelProperty mp : modelProperties )
        {
            if ( mp.getUri().equals( projectUri ) )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns all model properties containing the specified uri from the specified properties list.
     *
     * @param uri        the uri to use in finding the returned model properties
     * @param properties the model properties list to search
     * @return all model properties containing the specified uri from the specified properties list
     */
    public static List<ModelProperty> getPropertiesFor( String uri, List<ModelProperty> properties )
    {
        List<ModelProperty> modelProperties = new ArrayList<ModelProperty>();
        for ( ModelProperty mp : properties )
        {
            if ( uri.equals( mp.getUri() ) )
            {
                modelProperties.add( mp );
            }
        }
        return modelProperties;
    }


    /**
     * Returns the first model property containing the specified uri from the specified properties list.
     *
     * @param uri        the uri to use in finding the returned model property
     * @param properties the model properties list to search
     * @return the first model property containing the specified uri from the specified properties list.
     */
    public static ModelProperty getPropertyFor( String uri, List<ModelProperty> properties )
    {
        for ( ModelProperty mp : properties )
        {
            if ( uri.equals( mp.getUri() ) )
            {
                return mp;
            }
        }
        return null;
    }


}

