package org.apache.maven.project.injection;

import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.project.ModelUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Inject profile data into a Model, using the profile as the dominant data source, and 
 * persisting results of the injection in the Model.
 * 
 * This will look similar to the ModelUtils/DefaultModelInheritanceAssembler code, but 
 * they are distinct. In model inheritance, the child provides data dominance AND persists
 * the results of the merge...sort of a 'merge-out' system.
 * 
 * In this system, the profile is dominant, but the model receives the merge result...sort
 * of a 'merge-in' system. The two pieces of code look like they could be combined with a
 * set of flags to determine which direction to merge 'to', but there are enough differences
 * in the code to justify the extra code involved with separating them, in order to simplify 
 * the logic.
 */
public class DefaultProfileInjector
    implements ProfileInjector
{

    public void inject( Profile profile, Model model )
    {
        injectDependencies( profile, model );

        injectModules( profile, model );

        model.setRepositories( ModelUtils.mergeRepositoryLists( profile.getRepositories(), model.getRepositories() ) );
        model.setPluginRepositories( ModelUtils.mergeRepositoryLists( profile.getPluginRepositories(), model
            .getPluginRepositories() ) );

        injectReporting( profile, model );

        injectDependencyManagement( profile, model );

        injectDistributionManagement( profile, model );
        
        injectBuild( profile, model );
        
        Properties props = new Properties();
        props.putAll( model.getProperties() );
        props.putAll( profile.getProperties() );
        
        model.setProperties( props );
    }

    private void injectBuild( Profile profile, Model model )
    {
        BuildBase profileBuild = profile.getBuild();
        Build modelBuild = model.getBuild();
        
        // if the parent build is null, obviously we cannot inherit from it...
        if ( profileBuild != null )
        {
            if ( modelBuild == null )
            {
                modelBuild = new Build();
                model.setBuild( modelBuild );
            }
            
            if ( profileBuild.getDirectory() != null )
            {
                modelBuild.setDirectory( profileBuild.getDirectory() );
            }

            if ( profileBuild.getDefaultGoal() != null )
            {
                modelBuild.setDefaultGoal( profileBuild.getDefaultGoal() );
            }

            if ( profileBuild.getFinalName() != null )
            {
                modelBuild.setFinalName( profileBuild.getFinalName() );
            }

            List profileResources = profileBuild.getResources();
            
            if ( profileResources != null )
            {
                modelBuild.setResources( profileResources );
            }

            List profileTestResources = profileBuild.getTestResources();
            
            if ( profileTestResources != null )
            {
                modelBuild.setTestResources( profileTestResources );
            }
            
            injectPlugins( profileBuild, modelBuild );

            // Plugin management :: aggregate
            PluginManagement profilePM = profileBuild.getPluginManagement();
            PluginManagement modelPM = modelBuild.getPluginManagement();

            if ( modelPM == null )
            {
                modelBuild.setPluginManagement( profilePM );
            }
            else
            {
                injectPlugins( profilePM, modelPM );
            }
        }
    }

    private void injectPlugins( PluginContainer profileContainer, PluginContainer modelContainer )
    {
        List modelPlugins = modelContainer.getPlugins();
        
        if ( modelPlugins == null )
        {
            modelContainer.setPlugins( profileContainer.getPlugins() );
        }
        else if ( profileContainer.getPlugins() != null )
        {
            Map mergedPlugins = new TreeMap();

            Map profilePlugins = profileContainer.getPluginsAsMap();

            for ( Iterator it = modelPlugins.iterator(); it.hasNext(); )
            {
                Plugin modelPlugin = (Plugin) it.next();

                Plugin mergedPlugin = modelPlugin;

                Plugin profilePlugin = (Plugin) profilePlugins.get( modelPlugin.getKey() );

                if ( profilePlugin != null )
                {
                    mergedPlugin = modelPlugin;

                    injectPluginDefinition( profilePlugin, modelPlugin );
                }

                mergedPlugins.put( mergedPlugin.getKey(), mergedPlugin );
            }

            for ( Iterator it = profilePlugins.values().iterator(); it.hasNext(); )
            {
                Plugin profilePlugin = (Plugin) it.next();

                if ( !mergedPlugins.containsKey( profilePlugin.getKey() ) )
                {
                    mergedPlugins.put( profilePlugin.getKey(), profilePlugin );
                }
            }

            modelContainer.setPlugins( new ArrayList( mergedPlugins.values() ) );

            modelContainer.flushPluginMap();
        }
    }

    private void injectPluginDefinition( Plugin profilePlugin, Plugin modelPlugin )
    {
        if ( profilePlugin == null || modelPlugin == null )
        {
            // nothing to do.
            return;
        }

        if ( profilePlugin.isExtensions() )
        {
            modelPlugin.setExtensions( true );
        }

        if ( profilePlugin.getVersion() != null )
        {
            modelPlugin.setVersion( profilePlugin.getVersion() );
        }

        // merge the lists of goals that are not attached to an <execution/>
        injectConfigurationContainer( profilePlugin, modelPlugin );

        // from here to the end of the method is dealing with merging of the <executions/> section.
        List modelExecutions = modelPlugin.getExecutions();

        if ( modelExecutions == null || modelExecutions.isEmpty() )
        {
            modelPlugin.setExecutions( profilePlugin.getExecutions() );
        }
        else
        {
            Map executions = new TreeMap();

            Map profileExecutions = profilePlugin.getExecutionsAsMap();

            for ( Iterator it = modelExecutions.iterator(); it.hasNext(); )
            {
                PluginExecution modelExecution = (PluginExecution) it.next();

                PluginExecution profileExecution = (PluginExecution) profileExecutions.get( modelExecution.getId() );

                if ( profileExecution != null )
                {
                    injectConfigurationContainer( profileExecution, modelExecution );
                    
                    if ( profileExecution.getPhase() != null )
                    {
                        modelExecution.setPhase( profileExecution.getPhase() );
                    }
                    
                    List profileGoals = profileExecution.getGoals();
                    List modelGoals = modelExecution.getGoals();
                    
                    List goals = new ArrayList();
                    
                    if ( modelGoals != null && !modelGoals.isEmpty() )
                    {
                        goals.addAll( modelGoals );
                    }
                    
                    if ( profileGoals != null )
                    {
                        for ( Iterator goalIterator = profileGoals.iterator(); goalIterator.hasNext(); )
                        {
                            String goal = (String) goalIterator.next();
                            
                            if ( !goals.contains( goal ) )
                            {
                                goals.add( goal );
                            }
                        }
                    }
                    
                    modelExecution.setGoals( goals );
                }

                executions.put( modelExecution.getId(), modelExecution );
            }

            for ( Iterator it = profileExecutions.entrySet().iterator(); it.hasNext(); )
            {
                Map.Entry entry = (Map.Entry) it.next();

                String id = (String) entry.getKey();

                if ( !executions.containsKey( id ) )
                {
                    executions.put( id, entry.getValue() );
                }
            }

            modelPlugin.setExecutions( new ArrayList( executions.values() ) );

            modelPlugin.flushExecutionMap();
        }

    }

    private void injectConfigurationContainer( ConfigurationContainer profileContainer,
                                              ConfigurationContainer modelContainer )
    {
        Xpp3Dom configuration = (Xpp3Dom) profileContainer.getConfiguration();
        Xpp3Dom parentConfiguration = (Xpp3Dom) modelContainer.getConfiguration();

        configuration = Xpp3Dom.mergeXpp3Dom( configuration, parentConfiguration );

        modelContainer.setConfiguration( configuration );
    }

    private void injectModules( Profile profile, Model model )
    {
        List modules = new ArrayList();

        List profileModules = profile.getModules();

        if ( profileModules != null && !profileModules.isEmpty() )
        {
            modules.addAll( profileModules );
        }

        List modelModules = model.getModules();

        if ( modelModules != null )
        {
            for ( Iterator it = modelModules.iterator(); it.hasNext(); )
            {
                String module = (String) it.next();

                if ( !modules.contains( module ) )
                {
                    modules.add( module );
                }
            }
        }

        model.setModules( modules );
    }

    private void injectDistributionManagement( Profile profile, Model model )
    {
        DistributionManagement pDistMgmt = profile.getDistributionManagement();
        DistributionManagement mDistMgmt = model.getDistributionManagement();

        if ( mDistMgmt == null )
        {
            model.setDistributionManagement( pDistMgmt );
        }
        else if ( pDistMgmt != null )
        {
            if ( pDistMgmt.getRepository() != null )
            {
                mDistMgmt.setRepository( pDistMgmt.getRepository() );
            }

            if ( pDistMgmt.getSnapshotRepository() != null )
            {
                mDistMgmt.setSnapshotRepository( pDistMgmt.getSnapshotRepository() );
            }

            if ( StringUtils.isNotEmpty( pDistMgmt.getDownloadUrl() ) )
            {
                mDistMgmt.setDownloadUrl( pDistMgmt.getDownloadUrl() );
            }

            if ( pDistMgmt.getRelocation() != null )
            {
                mDistMgmt.setRelocation( pDistMgmt.getRelocation() );
            }

            if ( pDistMgmt.getSite() != null )
            {
                mDistMgmt.setSite( pDistMgmt.getSite() );
            }

            // NOTE: We SHOULD NOT be inheriting status, since this is an assessment of the POM quality.
        }
    }

    private void injectDependencyManagement( Profile profile, Model model )
    {
        DependencyManagement modelDepMgmt = model.getDependencyManagement();

        DependencyManagement profileDepMgmt = profile.getDependencyManagement();

        if ( profileDepMgmt != null )
        {
            if ( modelDepMgmt == null )
            {
                model.setDependencyManagement( profileDepMgmt );
            }
            else
            {
                Map depsMap = new HashMap();

                List deps = modelDepMgmt.getDependencies();

                if ( deps != null )
                {
                    for ( Iterator it = deps.iterator(); it.hasNext(); )
                    {
                        Dependency dependency = (Dependency) it.next();
                        depsMap.put( dependency.getManagementKey(), dependency );
                    }
                }

                deps = profileDepMgmt.getDependencies();

                if ( deps != null )
                {
                    for ( Iterator it = deps.iterator(); it.hasNext(); )
                    {
                        Dependency dependency = (Dependency) it.next();
                        depsMap.put( dependency.getManagementKey(), dependency );
                    }
                }

                modelDepMgmt.setDependencies( new ArrayList( depsMap.values() ) );
            }
        }
    }

    private void injectReporting( Profile profile, Model model )
    {
        // Reports :: aggregate
        Reporting profileReporting = profile.getReporting();
        Reporting modelReporting = model.getReporting();

        if ( profileReporting != null )
        {
            if ( modelReporting == null )
            {
                model.setReporting( profileReporting );
            }
            else
            {
                if ( StringUtils.isEmpty( modelReporting.getOutputDirectory() ) )
                {
                    modelReporting.setOutputDirectory( profileReporting.getOutputDirectory() );
                }

                Map mergedReportPlugins = new HashMap();

                Map profileReportersByKey = profileReporting.getReportPluginsAsMap();

                List modelReportPlugins = modelReporting.getPlugins();

                if ( modelReportPlugins != null )
                {
                    for ( Iterator it = modelReportPlugins.iterator(); it.hasNext(); )
                    {
                        ReportPlugin modelReportPlugin = (ReportPlugin) it.next();

                        String inherited = modelReportPlugin.getInherited();

                        if ( StringUtils.isEmpty( inherited ) || Boolean.valueOf( inherited ).booleanValue() )
                        {
                            ReportPlugin profileReportPlugin = (ReportPlugin) profileReportersByKey
                                .get( modelReportPlugin.getKey() );

                            ReportPlugin mergedReportPlugin = modelReportPlugin;

                            if ( profileReportPlugin != null )
                            {
                                mergedReportPlugin = profileReportPlugin;

                                mergeReportPlugins( profileReportPlugin, modelReportPlugin );
                            }
                            else if ( StringUtils.isEmpty( inherited ) )
                            {
                                mergedReportPlugin.unsetInheritanceApplied();
                            }

                            mergedReportPlugins.put( mergedReportPlugin.getKey(), mergedReportPlugin );
                        }
                    }
                }

                for ( Iterator it = profileReportersByKey.entrySet().iterator(); it.hasNext(); )
                {
                    Map.Entry entry = (Map.Entry) it.next();

                    String key = (String) entry.getKey();

                    if ( !mergedReportPlugins.containsKey( key ) )
                    {
                        mergedReportPlugins.put( key, entry.getValue() );
                    }
                }

                profileReporting.setPlugins( new ArrayList( mergedReportPlugins.values() ) );

                profileReporting.flushReportPluginMap();
            }
        }
    }

    private void mergeReportPlugins( ReportPlugin dominant, ReportPlugin recessive )
    {
        if ( StringUtils.isEmpty( recessive.getVersion() ) )
        {
            recessive.setVersion( dominant.getVersion() );
        }

        Xpp3Dom dominantConfig = (Xpp3Dom) dominant.getConfiguration();
        Xpp3Dom recessiveConfig = (Xpp3Dom) recessive.getConfiguration();

        recessive.setConfiguration( Xpp3Dom.mergeXpp3Dom( dominantConfig, recessiveConfig ) );

        Map mergedReportSets = new HashMap();

        Map dominantReportSetsById = dominant.getReportSetsAsMap();

        for ( Iterator it = recessive.getReportSets().iterator(); it.hasNext(); )
        {
            ReportSet recessiveReportSet = (ReportSet) it.next();

            ReportSet dominantReportSet = (ReportSet) dominantReportSetsById.get( recessiveReportSet.getId() );

            ReportSet merged = recessiveReportSet;

            if ( dominantReportSet != null )
            {
                merged = recessiveReportSet;

                Xpp3Dom dominantRSConfig = (Xpp3Dom) dominantReportSet.getConfiguration();
                Xpp3Dom mergedRSConfig = (Xpp3Dom) merged.getConfiguration();

                merged.setConfiguration( Xpp3Dom.mergeXpp3Dom( dominantRSConfig, mergedRSConfig ) );

                List mergedReports = merged.getReports();

                if ( mergedReports == null )
                {
                    mergedReports = new ArrayList();

                    merged.setReports( mergedReports );
                }

                List dominantRSReports = dominantReportSet.getReports();

                if ( dominantRSReports != null )
                {
                    for ( Iterator reportIterator = dominantRSReports.iterator(); reportIterator.hasNext(); )
                    {
                        String report = (String) reportIterator.next();

                        if ( !mergedReports.contains( report ) )
                        {
                            mergedReports.add( report );
                        }
                    }
                }

                mergedReportSets.put( merged.getId(), merged );
            }
        }

        for ( Iterator rsIterator = dominantReportSetsById.entrySet().iterator(); rsIterator.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) rsIterator.next();

            String key = (String) entry.getKey();

            if ( !mergedReportSets.containsKey( key ) )
            {
                mergedReportSets.put( key, entry.getValue() );
            }
        }

        recessive.setReportSets( new ArrayList( mergedReportSets.values() ) );

        recessive.flushReportSetMap();
    }

    private void injectDependencies( Profile profile, Model model )
    {
        Map depsMap = new HashMap();

        List deps = model.getDependencies();

        if ( deps != null )
        {
            for ( Iterator it = deps.iterator(); it.hasNext(); )
            {
                Dependency dependency = (Dependency) it.next();
                depsMap.put( dependency.getManagementKey(), dependency );
            }
        }

        deps = profile.getDependencies();

        if ( deps != null )
        {
            for ( Iterator it = deps.iterator(); it.hasNext(); )
            {
                Dependency dependency = (Dependency) it.next();
                depsMap.put( dependency.getManagementKey(), dependency );
            }
        }

        model.setDependencies( new ArrayList( depsMap.values() ) );
    }

}
