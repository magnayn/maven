package org.apache.maven.project;

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
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.project.overlay.BuildOverlay;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * The concern of the project is provide runtime values based on the model. <p/>
 * The values in the model remain untouched but during the process of building a
 * project notions like inheritance and interpolation can be added. This allows
 * to have an entity which is useful in a runtime while preserving the model so
 * that it can be marshalled and unmarshalled without being tainted by runtime
 * requirements. <p/>We need to leave the model intact because we don't want
 * the following:
 * <ol>
 * <li>We don't want interpolated values being written back into the model.
 * <li>We don't want inherited values being written back into the model.
 * </ol>
 */
public class MavenProject
{
    private Model model;

    private MavenProject parent;

    private File file;

    private Set artifacts;

    private Artifact parentArtifact;

    private Set pluginArtifacts;

    private List remoteArtifactRepositories;

    private List collectedProjects = Collections.EMPTY_LIST;

    private List attachedArtifacts;

    private MavenProject executionProject;

    private List compileSourceRoots = new ArrayList();

    private List testCompileSourceRoots = new ArrayList();

    private List scriptSourceRoots = new ArrayList();

    private List pluginArtifactRepositories;

    private ArtifactRepository releaseArtifactRepository;

    private ArtifactRepository snapshotArtifactRepository;

    private List activeProfiles = new ArrayList();

    private Set dependencyArtifacts;

    private Artifact artifact;

    // calculated.
    private Map artifactMap;

    private Model originalModel;

    private Map pluginArtifactMap;

    private Set reportArtifacts;

    private Map reportArtifactMap;

    private Set extensionArtifacts;

    private Map extensionArtifactMap;

    private Map projectReferences = new HashMap();

    private Build buildOverlay;

    private boolean executionRoot;

    public MavenProject( Model model )
    {
        this.model = model;
    }

    public MavenProject( MavenProject project )
    {
        // disown the parent

        // copy fields
        this.file = project.file;

        // don't need a deep copy, they don't get modified or added/removed to/from - but make them unmodifiable to be sure!
        if ( project.dependencyArtifacts != null )
        {
            this.dependencyArtifacts = Collections.unmodifiableSet( project.dependencyArtifacts );
        }
        if ( project.artifacts != null )
        {
            this.artifacts = Collections.unmodifiableSet( project.artifacts );
        }
        this.pluginArtifacts = Collections.unmodifiableSet( project.pluginArtifacts );
        this.reportArtifacts = Collections.unmodifiableSet( project.reportArtifacts );
        this.extensionArtifacts = Collections.unmodifiableSet( project.extensionArtifacts );
        this.parentArtifact = project.parentArtifact;

        this.remoteArtifactRepositories = Collections.unmodifiableList( project.remoteArtifactRepositories );
        this.pluginArtifactRepositories = Collections.unmodifiableList( project.pluginArtifactRepositories );
        this.collectedProjects = Collections.unmodifiableList( project.collectedProjects );
        this.activeProfiles = Collections.unmodifiableList( project.activeProfiles );

        // clone properties modifyable by plugins in a forked lifecycle
        this.attachedArtifacts = new ArrayList( project.getAttachedArtifacts() );

        // no need for execution project

        // clone source roots
        this.compileSourceRoots = new ArrayList( project.compileSourceRoots );
        this.testCompileSourceRoots = new ArrayList( project.testCompileSourceRoots );
        this.scriptSourceRoots = new ArrayList( project.scriptSourceRoots );

        this.model = ModelUtils.cloneModel( project.model );

        if ( project.originalModel != null )
        {
            this.originalModel = ModelUtils.cloneModel( project.originalModel );
        }

        this.executionRoot = project.executionRoot;

        // TODO: need to clone this too?
        this.artifact = project.artifact;
    }

    // ----------------------------------------------------------------------
    // Accessors
    // ----------------------------------------------------------------------

    public Artifact getArtifact()
    {
        return artifact;
    }

    public void setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
    }

    //@todo I would like to get rid of this. jvz.
    public Model getModel()
    {
        return model;
    }

    public MavenProject getParent()
    {
        return parent;
    }

    public void setParent( MavenProject parent )
    {
        this.parent = parent;
    }

    public void setRemoteArtifactRepositories( List remoteArtifactRepositories )
    {
        this.remoteArtifactRepositories = remoteArtifactRepositories;
    }

    public List getRemoteArtifactRepositories()
    {
        return remoteArtifactRepositories;
    }

    public boolean hasParent()
    {
        return getParent() != null;
    }

    public File getFile()
    {
        return file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    public File getBasedir()
    {
        if ( getFile() != null )
        {
            return getFile().getParentFile();
        }
        else
        {
            // repository based POM
            return null;
        }
    }

    public void setDependencies( List dependencies )
    {
        model.setDependencies( dependencies );
    }

    public List getDependencies()
    {
        return model.getDependencies();
    }

    public DependencyManagement getDependencyManagement()
    {
        return model.getDependencyManagement();
    }

    // ----------------------------------------------------------------------
    // Test and compile sourceroots.
    // ----------------------------------------------------------------------

    public void addCompileSourceRoot( String path )
    {
        if ( path != null )
        {
            path = path.trim();
            if ( path.length() != 0 )
            {
                if ( !compileSourceRoots.contains( path ) )
                {
                    compileSourceRoots.add( path );
                }
            }
        }
    }

    public void addScriptSourceRoot( String path )
    {
        if ( path != null )
        {
            path = path.trim();
            if ( path.length() != 0 )
            {
                if ( !scriptSourceRoots.contains( path ) )
                {
                    scriptSourceRoots.add( path );
                }
            }
        }
    }

    public void addTestCompileSourceRoot( String path )
    {
        if ( path != null )
        {
            path = path.trim();
            if ( path.length() != 0 )
            {
                if ( !testCompileSourceRoots.contains( path ) )
                {
                    testCompileSourceRoots.add( path );
                }
            }
        }
    }

    public List getCompileSourceRoots()
    {
        return compileSourceRoots;
    }

    public List getScriptSourceRoots()
    {
        return scriptSourceRoots;
    }

    public List getTestCompileSourceRoots()
    {
        return testCompileSourceRoots;
    }

    public List getCompileClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List list = new ArrayList( getArtifacts().size() );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) ||
                    Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    String refId = getProjectReferenceId( a.getGroupId(), a.getArtifactId() );
                    MavenProject project = (MavenProject) projectReferences.get( refId );
                    if ( project != null )
                    {
                        list.add( project.getBuild().getOutputDirectory() );
                    }
                    else
                    {
                        File file = a.getFile();
                        if ( file == null )
                        {
                            throw new DependencyResolutionRequiredException( a );
                        }
                        list.add( file.getPath() );
                    }
                }
            }
        }
        return list;
    }

    public List getCompileArtifacts()
    {
        List list = new ArrayList( getArtifacts().size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) ||
                    Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    list.add( a );
                }
            }
        }
        return list;
    }

    public List getCompileDependencies()
    {
        Set artifacts = getArtifacts();

        if ( artifacts == null || artifacts.isEmpty() )
        {
            return Collections.EMPTY_LIST;
        }

        List list = new ArrayList( artifacts.size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: let the scope handler deal with this
            if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) ||
                Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
            {
                Dependency dependency = new Dependency();

                dependency.setArtifactId( a.getArtifactId() );
                dependency.setGroupId( a.getGroupId() );
                dependency.setVersion( a.getVersion() );
                dependency.setScope( a.getScope() );
                dependency.setType( a.getType() );
                dependency.setClassifier( a.getClassifier() );

                list.add( dependency );
            }
        }
        return list;
    }

    public List getTestClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List list = new ArrayList( getArtifacts().size() + 1 );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                // NOTE: [jc] scope == 'test' is the widest possible scope, so we don't really need to perform
                // this check...
                // if ( Artifact.SCOPE_TEST.equals( a.getScope() ) || Artifact.SCOPE_COMPILE.equals( a.getScope() ) ||
                //     Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
                // {
                // }
                File file = a.getFile();
                if ( file == null )
                {
                    throw new DependencyResolutionRequiredException( a );
                }
                list.add( file.getPath() );
            }
        }
        return list;
    }

    public List getTestArtifacts()
    {
        List list = new ArrayList( getArtifacts().size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                // NOTE: [jc] scope == 'test' is the widest possible scope, so we don't really need to perform
                // this check...
                // if ( Artifact.SCOPE_TEST.equals( a.getScope() ) || Artifact.SCOPE_COMPILE.equals( a.getScope() ) ||
                //      Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
                // {
                //     list.add( a );
                // }

                list.add( a );
            }
        }
        return list;
    }

    public List getTestDependencies()
    {
        Set artifacts = getArtifacts();

        if ( artifacts == null || artifacts.isEmpty() )
        {
            return Collections.EMPTY_LIST;
        }

        List list = new ArrayList( artifacts.size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: let the scope handler deal with this
            // NOTE: [jc] scope == 'test' is the widest possible scope, so we don't really need to perform
            // this check...
            // if ( Artifact.SCOPE_TEST.equals( a.getScope() ) || Artifact.SCOPE_COMPILE.equals( a.getScope() ) ||
            //     Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
            // {
            // }

            Dependency dependency = new Dependency();

            dependency.setArtifactId( a.getArtifactId() );
            dependency.setGroupId( a.getGroupId() );
            dependency.setVersion( a.getVersion() );
            dependency.setScope( a.getScope() );
            dependency.setType( a.getType() );
            dependency.setClassifier( a.getClassifier() );

            list.add( dependency );
        }
        return list;
    }

    public List getRuntimeClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List list = new ArrayList( getArtifacts().size() + 1 );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
                {
                    File file = a.getFile();
                    if ( file == null )
                    {
                        throw new DependencyResolutionRequiredException( a );
                    }
                    list.add( file.getPath() );
                }
            }
        }
        return list;
    }

    public List getRuntimeArtifacts()
    {
        List list = new ArrayList( getArtifacts().size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
                {
                    list.add( a );
                }
            }
        }
        return list;
    }

    public List getRuntimeDependencies()
    {
        Set artifacts = getArtifacts();

        if ( artifacts == null || artifacts.isEmpty() )
        {
            return Collections.EMPTY_LIST;
        }

        List list = new ArrayList( artifacts.size() );

        for ( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: let the scope handler deal with this
            if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_RUNTIME.equals( a.getScope() ) )
            {
                Dependency dependency = new Dependency();

                dependency.setArtifactId( a.getArtifactId() );
                dependency.setGroupId( a.getGroupId() );
                dependency.setVersion( a.getVersion() );
                dependency.setScope( a.getScope() );
                dependency.setType( a.getType() );
                dependency.setClassifier( a.getClassifier() );

                list.add( dependency );
            }
        }
        return list;
    }

    public List getSystemClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List list = new ArrayList( getArtifacts().size() );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    String refId = getProjectReferenceId( a.getGroupId(), a.getArtifactId() );
                    MavenProject project = (MavenProject) projectReferences.get( refId );
                    if ( project != null )
                    {
                        list.add( project.getBuild().getOutputDirectory() );
                    }
                    else
                    {
                        File file = a.getFile();
                        if ( file == null )
                        {
                            throw new DependencyResolutionRequiredException( a );
                        }
                        list.add( file.getPath() );
                    }
                }
            }
        }
        return list;
    }

    public List getSystemArtifacts()
    {
        List list = new ArrayList( getArtifacts().size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( isAddedToClasspath( a ) )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    list.add( a );
                }
            }
        }
        return list;
    }

    public List getSystemDependencies()
    {
        Set artifacts = getArtifacts();

        if ( artifacts == null || artifacts.isEmpty() )
        {
            return Collections.EMPTY_LIST;
        }

        List list = new ArrayList( artifacts.size() );

        for ( Iterator i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: let the scope handler deal with this
            if ( Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
            {
                Dependency dependency = new Dependency();

                dependency.setArtifactId( a.getArtifactId() );
                dependency.setGroupId( a.getGroupId() );
                dependency.setVersion( a.getVersion() );
                dependency.setScope( a.getScope() );
                dependency.setType( a.getType() );
                dependency.setClassifier( a.getClassifier() );

                list.add( dependency );
            }
        }
        return list;
    }

    private static boolean isAddedToClasspath( Artifact artifact )
    {
        String type = artifact.getType();

        // TODO: utilise type handler
        return "jar".equals( type ) || "ejb".equals( type ) || "ejb-client".equals( type ) || "test-jar".equals( type );
    }

    // ----------------------------------------------------------------------
    // Delegate to the model
    // ----------------------------------------------------------------------

    public void setModelVersion( String pomVersion )
    {
        model.setModelVersion( pomVersion );
    }

    public String getModelVersion()
    {
        return model.getModelVersion();
    }

    public String getId()
    {
        return model.getId();
    }

    public void setGroupId( String groupId )
    {
        model.setGroupId( groupId );
    }

    public String getGroupId()
    {
        return model.getGroupId();
    }

    public void setArtifactId( String artifactId )
    {
        model.setArtifactId( artifactId );
    }

    public String getArtifactId()
    {
        return model.getArtifactId();
    }

    public void setName( String name )
    {
        model.setName( name );
    }

    public String getName()
    {
        // TODO: this should not be allowed to be null.
        if ( model.getName() != null )
        {
            return model.getName();
        }
        else
        {
            return getId();
        }
    }

    public void setVersion( String version )
    {
        model.setVersion( version );
    }

    public String getVersion()
    {
        return model.getVersion();
    }

    public String getPackaging()
    {
        return model.getPackaging();
    }

    public void setPackaging( String packaging )
    {
        model.setPackaging( packaging );
    }

    public void setInceptionYear( String inceptionYear )
    {
        model.setInceptionYear( inceptionYear );
    }

    public String getInceptionYear()
    {
        return model.getInceptionYear();
    }

    public void setUrl( String url )
    {
        model.setUrl( url );
    }

    public String getUrl()
    {
        return model.getUrl();
    }

    public Prerequisites getPrerequisites()
    {
        return model.getPrerequisites();
    }

    public void setIssueManagement( IssueManagement issueManagement )
    {
        model.setIssueManagement( issueManagement );
    }

    public CiManagement getCiManagement()
    {
        return model.getCiManagement();
    }

    public void setCiManagement( CiManagement ciManagement )
    {
        model.setCiManagement( ciManagement );
    }

    public IssueManagement getIssueManagement()
    {
        return model.getIssueManagement();
    }

    public void setDistributionManagement( DistributionManagement distributionManagement )
    {
        model.setDistributionManagement( distributionManagement );
    }

    public DistributionManagement getDistributionManagement()
    {
        return model.getDistributionManagement();
    }

    public void setDescription( String description )
    {
        model.setDescription( description );
    }

    public String getDescription()
    {
        return model.getDescription();
    }

    public void setOrganization( Organization organization )
    {
        model.setOrganization( organization );
    }

    public Organization getOrganization()
    {
        return model.getOrganization();
    }

    public void setScm( Scm scm )
    {
        model.setScm( scm );
    }

    public Scm getScm()
    {
        return model.getScm();
    }

    public void setMailingLists( List mailingLists )
    {
        model.setMailingLists( mailingLists );
    }

    public List getMailingLists()
    {
        return model.getMailingLists();
    }

    public void addMailingList( MailingList mailingList )
    {
        model.addMailingList( mailingList );
    }

    public void setDevelopers( List developers )
    {
        model.setDevelopers( developers );
    }

    public List getDevelopers()
    {
        return model.getDevelopers();
    }

    public void addDeveloper( Developer developer )
    {
        model.addDeveloper( developer );
    }

    public void setContributors( List contributors )
    {
        model.setContributors( contributors );
    }

    public List getContributors()
    {
        return model.getContributors();
    }

    public void addContributor( Contributor contributor )
    {
        model.addContributor( contributor );
    }

    public void setBuild( Build build )
    {
        this.buildOverlay = new BuildOverlay( build );

        model.setBuild( build );
    }

    public Build getBuild()
    {
        if ( buildOverlay == null )
        {
            buildOverlay = new BuildOverlay( model.getBuild() );
        }

        return buildOverlay;
    }

    public List getResources()
    {
        return getBuild().getResources();
    }

    public List getTestResources()
    {
        return getBuild().getTestResources();
    }

    public void addResource( Resource resource )
    {
        getBuild().addResource( resource );
    }

    public void addTestResource( Resource testResource )
    {
        getBuild().addTestResource( testResource );
    }

    public void setReporting( Reporting reporting )
    {
        model.setReporting( reporting );
    }

    public Reporting getReporting()
    {
        return model.getReporting();
    }

    public void setLicenses( List licenses )
    {
        model.setLicenses( licenses );
    }

    public List getLicenses()
    {
        return model.getLicenses();
    }

    public void addLicense( License license )
    {
        model.addLicense( license );
    }

    public void setArtifacts( Set artifacts )
    {
        this.artifacts = artifacts;

        // flush the calculated artifactMap
        this.artifactMap = null;
    }

    public Set getArtifacts()
    {
        return artifacts == null ? Collections.EMPTY_SET : artifacts;
    }

    public Map getArtifactMap()
    {
        if ( artifactMap == null )
        {
            artifactMap = ArtifactUtils.artifactMapByVersionlessId( getArtifacts() );
        }

        return artifactMap;
    }

    public void setPluginArtifacts( Set pluginArtifacts )
    {
        this.pluginArtifacts = pluginArtifacts;

        this.pluginArtifactMap = null;
    }

    public Set getPluginArtifacts()
    {
        return pluginArtifacts;
    }

    public Map getPluginArtifactMap()
    {
        if ( pluginArtifactMap == null )
        {
            pluginArtifactMap = ArtifactUtils.artifactMapByVersionlessId( getPluginArtifacts() );
        }

        return pluginArtifactMap;
    }

    public void setReportArtifacts( Set reportArtifacts )
    {
        this.reportArtifacts = reportArtifacts;

        this.reportArtifactMap = null;
    }

    public Set getReportArtifacts()
    {
        return reportArtifacts;
    }

    public Map getReportArtifactMap()
    {
        if ( reportArtifactMap == null )
        {
            reportArtifactMap = ArtifactUtils.artifactMapByVersionlessId( getReportArtifacts() );
        }

        return reportArtifactMap;
    }

    public void setExtensionArtifacts( Set extensionArtifacts )
    {
        this.extensionArtifacts = extensionArtifacts;

        this.extensionArtifactMap = null;
    }

    public Set getExtensionArtifacts()
    {
        return this.extensionArtifacts;
    }

    public Map getExtensionArtifactMap()
    {
        if ( extensionArtifactMap == null )
        {
            extensionArtifactMap = ArtifactUtils.artifactMapByVersionlessId( getExtensionArtifacts() );
        }

        return extensionArtifactMap;
    }

    public void setParentArtifact( Artifact parentArtifact )
    {
        this.parentArtifact = parentArtifact;
    }

    public Artifact getParentArtifact()
    {
        return parentArtifact;
    }

    public List getRepositories()
    {
        return model.getRepositories();
    }

    // ----------------------------------------------------------------------
    // Plugins
    // ----------------------------------------------------------------------

    public List getReportPlugins()
    {
        if ( model.getReporting() == null )
        {
            return null;
        }
        return model.getReporting().getPlugins();

    }

    public List getBuildPlugins()
    {
        if ( model.getBuild() == null )
        {
            return null;
        }
        return model.getBuild().getPlugins();
    }

    public List getModules()
    {
        return model.getModules();
    }

    public PluginManagement getPluginManagement()
    {
        PluginManagement pluginMgmt = null;

        Build build = model.getBuild();
        if ( build != null )
        {
            pluginMgmt = build.getPluginManagement();
        }

        return pluginMgmt;
    }

    public void addPlugin( Plugin plugin )
    {
        Build build = model.getBuild();

        if ( build == null )
        {
            build = new Build();

            model.setBuild( build );
        }

        if ( !build.getPluginsAsMap().containsKey( plugin.getKey() ) )
        {
            build.addPlugin( plugin );
            build.flushPluginMap();
        }
    }

    public List getCollectedProjects()
    {
        return collectedProjects;
    }

    public void setCollectedProjects( List collectedProjects )
    {
        this.collectedProjects = collectedProjects;
    }

    public void setPluginArtifactRepositories( List pluginArtifactRepositories )
    {
        this.pluginArtifactRepositories = pluginArtifactRepositories;
    }

    /**
     * @return a list of ArtifactRepository objects constructed
     *         from the Repository objects returned by getPluginRepositories.
     */
    public List getPluginArtifactRepositories()
    {
        return pluginArtifactRepositories;
    }

    public ArtifactRepository getDistributionManagementArtifactRepository()
    {
        return getArtifact().isSnapshot() && snapshotArtifactRepository != null ? snapshotArtifactRepository
            : releaseArtifactRepository;
    }

    public List getPluginRepositories()
    {
        return model.getPluginRepositories();
    }

    public void setActiveProfiles( List activeProfiles )
    {
        this.activeProfiles.addAll( activeProfiles );
    }

    public List getActiveProfiles()
    {
        return activeProfiles;
    }

    public void addAttachedArtifact( Artifact artifact )
    {
        getAttachedArtifacts().add( artifact );
    }

    public List getAttachedArtifacts()
    {
        if ( attachedArtifacts == null )
        {
            attachedArtifacts = new ArrayList();
        }
        return attachedArtifacts;
    }

    public Xpp3Dom getGoalConfiguration( String pluginGroupId, String pluginArtifactId, String executionId,
                                         String goalId )
    {
        Xpp3Dom dom = null;

        // ----------------------------------------------------------------------
        // I would like to be able to lookup the Mojo object using a key but
        // we have a limitation in modello that will be remedied shortly. So
        // for now I have to iterate through and see what we have.
        // ----------------------------------------------------------------------

        if ( getBuildPlugins() != null )
        {
            for ( Iterator iterator = getBuildPlugins().iterator(); iterator.hasNext(); )
            {
                Plugin plugin = (Plugin) iterator.next();

                if ( pluginGroupId.equals( plugin.getGroupId() ) && pluginArtifactId.equals( plugin.getArtifactId() ) )
                {
                    dom = (Xpp3Dom) plugin.getConfiguration();

                    if ( executionId != null )
                    {
                        PluginExecution execution = (PluginExecution) plugin.getExecutionsAsMap().get( executionId );
                        if ( execution != null )
                        {
                            Xpp3Dom executionConfiguration = (Xpp3Dom) execution.getConfiguration();
                            if ( executionConfiguration != null )
                            {
                                Xpp3Dom newDom = new Xpp3Dom( executionConfiguration );
                                dom = Xpp3Dom.mergeXpp3Dom( newDom, dom );
                            }
                        }
                    }
                    break;
                }
            }
        }

        if ( dom != null )
        {
            // make a copy so the original in the POM doesn't get messed with
            dom = new Xpp3Dom( dom );
        }

        return dom;
    }

    public Xpp3Dom getReportConfiguration( String pluginGroupId, String pluginArtifactId, String reportSetId )
    {
        Xpp3Dom dom = null;

        // ----------------------------------------------------------------------
        // I would like to be able to lookup the Mojo object using a key but
        // we have a limitation in modello that will be remedied shortly. So
        // for now I have to iterate through and see what we have.
        // ----------------------------------------------------------------------

        if ( getReportPlugins() != null )
        {
            for ( Iterator iterator = getReportPlugins().iterator(); iterator.hasNext(); )
            {
                ReportPlugin plugin = (ReportPlugin) iterator.next();

                if ( pluginGroupId.equals( plugin.getGroupId() ) && pluginArtifactId.equals( plugin.getArtifactId() ) )
                {
                    dom = (Xpp3Dom) plugin.getConfiguration();

                    if ( reportSetId != null )
                    {
                        ReportSet reportSet = (ReportSet) plugin.getReportSetsAsMap().get( reportSetId );
                        if ( reportSet != null )
                        {
                            Xpp3Dom executionConfiguration = (Xpp3Dom) reportSet.getConfiguration();
                            if ( executionConfiguration != null )
                            {
                                Xpp3Dom newDom = new Xpp3Dom( executionConfiguration );
                                dom = Xpp3Dom.mergeXpp3Dom( newDom, dom );
                            }
                        }
                    }
                    break;
                }
            }
        }

        if ( dom != null )
        {
            // make a copy so the original in the POM doesn't get messed with
            dom = new Xpp3Dom( dom );
        }

        return dom;
    }

    public MavenProject getExecutionProject()
    {
        return executionProject;
    }

    public void setExecutionProject( MavenProject executionProject )
    {
        this.executionProject = executionProject;
    }

    public void writeModel( Writer writer )
        throws IOException
    {
        MavenXpp3Writer pomWriter = new MavenXpp3Writer();

        pomWriter.write( writer, getModel() );
    }

    public void writeOriginalModel( Writer writer )
        throws IOException
    {
        MavenXpp3Writer pomWriter = new MavenXpp3Writer();

        pomWriter.write( writer, getOriginalModel() );
    }

    public Set getDependencyArtifacts()
    {
        return dependencyArtifacts;
    }

    public void setDependencyArtifacts( Set dependencyArtifacts )
    {
        this.dependencyArtifacts = dependencyArtifacts;
    }

    public void setReleaseArtifactRepository( ArtifactRepository releaseArtifactRepository )
    {
        this.releaseArtifactRepository = releaseArtifactRepository;
    }

    public void setSnapshotArtifactRepository( ArtifactRepository snapshotArtifactRepository )
    {
        this.snapshotArtifactRepository = snapshotArtifactRepository;
    }

    public void setOriginalModel( Model originalModel )
    {
        this.originalModel = originalModel;
    }

    public Model getOriginalModel()
    {
        return originalModel;
    }

    public boolean equals( Object other )
    {
        if ( other == this )
        {
            return true;
        }
        else if ( !( other instanceof MavenProject ) )
        {
            return false;
        }
        else
        {
            MavenProject otherProject = (MavenProject) other;

            return getId().equals( otherProject.getId() );
        }
    }

    public int hashCode()
    {
        return getId().hashCode();
    }

    public List getBuildExtensions()
    {
        Build build = getBuild();
        if ( build == null || build.getExtensions() == null )
        {
            return Collections.EMPTY_LIST;
        }
        else
        {
            return build.getExtensions();
        }
    }

    /**
     * @todo the lazy initialisation of this makes me uneasy.
     */
    public Set createArtifacts( ArtifactFactory artifactFactory, String inheritedScope,
                                ArtifactFilter dependencyFilter )
        throws InvalidVersionSpecificationException
    {
        return MavenMetadataSource.createArtifacts( artifactFactory, getDependencies(), inheritedScope,
                                                    dependencyFilter, projectReferences );
    }

    public void addProjectReference( MavenProject project )
    {
        projectReferences.put( getProjectReferenceId( project.getGroupId(), project.getArtifactId() ), project );
    }

    private static String getProjectReferenceId( String groupId, String artifactId )
    {
        return groupId + ":" + artifactId;
    }

    public void attachArtifact( String type, String classifier, File file )
    {
    }

    public Properties getProperties()
    {
        return getModel().getProperties();
    }

    public List getFilters()
    {
        return getBuild().getFilters();
    }

    public Map getProjectReferences()
    {
        return projectReferences;
    }

    public boolean isExecutionRoot()
    {
        return executionRoot;
    }

    public void setExecutionRoot( boolean executionRoot )
    {
        this.executionRoot = executionRoot;
    }

    public String getDefaultGoal()
    {
        return getBuild() != null ? getBuild().getDefaultGoal() : null;
    }
}
