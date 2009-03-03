package org.apache.maven.project;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.ManagedVersionMap;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Extension;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.ReportSet;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.artifact.ActiveProjectArtifact;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.repository.MavenRepositorySystem;
import org.apache.maven.repository.MavenRepositoryWrapper;
import org.apache.maven.repository.VersionNotFoundException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * The concern of the project is provide runtime values based on the model.
 * <p/>
 * The values in the model remain untouched but during the process of building a project notions
 * like inheritance and interpolation can be added. This allows to have an entity which is useful in
 * a runtime while preserving the model so that it can be marshalled and unmarshalled without being
 * tainted by runtime requirements.
 * <p/>
 * We need to leave the model intact because we don't want the following:
 * <ol>
 * <li>We don't want interpolated values being written back into the model.
 * <li>We don't want inherited values being written back into the model.
 * </ol>
 */
public class MavenProject
    implements Cloneable, MavenRepositoryWrapper
{
    public static final String EMPTY_PROJECT_GROUP_ID = "unknown";

    public static final String EMPTY_PROJECT_ARTIFACT_ID = "empty-project";

    public static final String EMPTY_PROJECT_VERSION = "0";

    private Model model;

    private MavenProject parent;

    private File file;

    private Set<Artifact> artifacts;

    private Artifact parentArtifact;

    private Set<Artifact> pluginArtifacts;

    private List<ArtifactRepository> remoteArtifactRepositories;

    private List<MavenProject> collectedProjects = Collections.emptyList();

    private List<Artifact> attachedArtifacts;

    private MavenProject executionProject;

    private List<String> compileSourceRoots = new ArrayList<String>();

    private List<String> testCompileSourceRoots = new ArrayList<String>();

    private List<String> scriptSourceRoots = new ArrayList<String>();

    private List<ArtifactRepository> pluginArtifactRepositories;

    private ArtifactRepository releaseArtifactRepository;

    private ArtifactRepository snapshotArtifactRepository;

    private List<Profile> activeProfiles = new ArrayList<Profile>();

    private Set<Artifact> dependencyArtifacts;

    private Artifact artifact;

    // calculated.
    private Map<String, Artifact> artifactMap;

    private Model originalModel;

    private Map<String, Artifact> pluginArtifactMap;

    private Set<Artifact> reportArtifacts;

    private Map<String, Artifact> reportArtifactMap;

    private Set<Artifact> extensionArtifacts;

    private Map<String, Artifact> extensionArtifactMap;

    private Map<String, Artifact> managedVersionMap;

    private Map<String, MavenProject> projectReferences = new HashMap<String, MavenProject>();

    private boolean executionRoot;

    private Map<String, String> moduleAdjustments;

    private Stack<MavenProject> previousExecutionProjects = new Stack<MavenProject>();

    private MavenProjectBuilder mavenProjectBuilder;

    private ProjectBuilderConfiguration projectBuilderConfiguration;

    private MavenRepositorySystem repositorySystem;
    //

    private File parentFile;

    public File getParentFile()
    {
        return parentFile;
    }

    public void setParentFile( File parentFile )
    {
        this.parentFile = parentFile;
    }

    public MavenProject()
    {
        Model model = new Model();

        model.setGroupId( EMPTY_PROJECT_GROUP_ID );
        model.setArtifactId( EMPTY_PROJECT_ARTIFACT_ID );
        model.setVersion( EMPTY_PROJECT_VERSION );

        setModel( model );
    }

    public MavenProject( Model model )
    {
        if ( model == null )
        {
            throw new IllegalArgumentException( "model: null" );
        }
        setModel( model );
    }

    /**
     * Constructor
     * 
     * @param model - may not be null
     * @param artifactFactory - may not be null
     * @param repositorySystem - may not be null
     * @param mavenProjectBuilder
     * @param projectBuilderConfiguration
     * @throws InvalidRepositoryException
     */
    public MavenProject( Model model, MavenRepositorySystem repositorySystem, MavenProjectBuilder mavenProjectBuilder, ProjectBuilderConfiguration projectBuilderConfiguration )
        throws InvalidRepositoryException
    {
        if ( model == null )
        {
            throw new IllegalArgumentException( "model: null" );
        }

        if ( repositorySystem == null )
        {
            throw new IllegalArgumentException( "mavenTools: null" );
        }

        setModel( model );
        this.mavenProjectBuilder = mavenProjectBuilder;
        this.projectBuilderConfiguration = projectBuilderConfiguration;
        this.repositorySystem = repositorySystem;
        originalModel = model;
        DistributionManagement dm = model.getDistributionManagement();

        if ( dm != null )
        {
            ArtifactRepository repo = repositorySystem.buildArtifactRepository( dm.getRepository() );
            setReleaseArtifactRepository( repo );

            if ( dm.getSnapshotRepository() != null )
            {
                repo = repositorySystem.buildArtifactRepository( dm.getSnapshotRepository() );
                setSnapshotArtifactRepository( repo );
            }
        }

        try
        {
            Set<Repository> repoSet = new LinkedHashSet<Repository>();
            if ( ( model.getRepositories() != null ) && !model.getRepositories().isEmpty() )
            {
                repoSet.addAll( model.getRepositories() );
            }

            if ( ( model.getPluginRepositories() != null ) && !model.getPluginRepositories().isEmpty() )
            {
                repoSet.addAll( model.getPluginRepositories() );
            }

            setRemoteArtifactRepositories( repositorySystem.buildArtifactRepositories( new ArrayList<Repository>( repoSet ) ) );
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
    }

    /**
     * @deprecated use {@link #clone()} so subclasses can provide a copy of the same class
     */
    @Deprecated
    public MavenProject( MavenProject project )
    {
        deepCopy( project );
    }

    // TODO: Find a way to use <relativePath/> here...it's tricky, because the moduleProject
    // usually doesn't have a file associated with it yet.
    public String getModulePathAdjustment( MavenProject moduleProject )
        throws IOException
    {
        // FIXME: This is hacky. What if module directory doesn't match artifactid, and parent
        // is coming from the repository??
        String module = moduleProject.getArtifactId();

        File moduleFile = moduleProject.getFile();

        if ( moduleFile != null )
        {
            File moduleDir = moduleFile.getCanonicalFile().getParentFile();

            module = moduleDir.getName();
        }

        if ( moduleAdjustments == null )
        {
            moduleAdjustments = new HashMap<String, String>();

            List<String> modules = getModules();
            if ( modules != null )
            {
                for ( Iterator<String> it = modules.iterator(); it.hasNext(); )
                {
                    String modulePath = (String) it.next();
                    String moduleName = modulePath;

                    if ( moduleName.endsWith( "/" ) || moduleName.endsWith( "\\" ) )
                    {
                        moduleName = moduleName.substring( 0, moduleName.length() - 1 );
                    }

                    int lastSlash = moduleName.lastIndexOf( '/' );

                    if ( lastSlash < 0 )
                    {
                        lastSlash = moduleName.lastIndexOf( '\\' );
                    }

                    String adjustment = null;

                    if ( lastSlash > -1 )
                    {
                        moduleName = moduleName.substring( lastSlash + 1 );
                        adjustment = modulePath.substring( 0, lastSlash );
                    }

                    moduleAdjustments.put( moduleName, adjustment );
                }
            }
        }

        return (String) moduleAdjustments.get( module );
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
        if ( parent == null )
        {
            if ( parentFile != null )
            {
                try
                {
                    parent = mavenProjectBuilder.build( parentFile, projectBuilderConfiguration );
                }
                catch ( ProjectBuildingException e )
                {
                    e.printStackTrace();
                }
            }
            else if ( model.getParent() != null )
            {
                try
                {
                    parent = mavenProjectBuilder.buildFromRepository( getParentArtifact(), this.remoteArtifactRepositories, projectBuilderConfiguration.getLocalRepository() );
                }
                catch ( ProjectBuildingException e )
                {
                    e.printStackTrace();
                }
            }
        }
        return parent;
    }

    public void setParent( MavenProject parent )
    {
        this.parent = parent;
    }

    public void setRemoteArtifactRepositories( List<ArtifactRepository> remoteArtifactRepositories )
    {
        this.remoteArtifactRepositories = remoteArtifactRepositories;
    }

    public List<ArtifactRepository> getRemoteArtifactRepositories()
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

    public void setDependencies( List<Dependency> dependencies )
    {
        getModel().setDependencies( dependencies );
    }

    public List<Dependency> getDependencies()
    {
        return getModel().getDependencies();
    }

    public DependencyManagement getDependencyManagement()
    {
        return getModel().getDependencyManagement();
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
                if ( !getCompileSourceRoots().contains( path ) )
                {
                    getCompileSourceRoots().add( path );
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
                if ( !getScriptSourceRoots().contains( path ) )
                {
                    getScriptSourceRoots().add( path );
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
                if ( !getTestCompileSourceRoots().contains( path ) )
                {
                    getTestCompileSourceRoots().add( path );
                }
            }
        }
    }

    public List<String> getCompileSourceRoots()
    {
        return compileSourceRoots;
    }

    public List<String> getScriptSourceRoots()
    {
        return scriptSourceRoots;
    }

    public List<String> getTestCompileSourceRoots()
    {
        return testCompileSourceRoots;
    }

    public List<String> getCompileClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List<String> list = new ArrayList<String>( getArtifacts().size() + 1 );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactHandler().isAddedToClasspath() )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) || Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    addArtifactPath( a, list );
                }
            }
        }
        return list;
    }

    public List<Artifact> getCompileArtifacts()
    {
        List<Artifact> list = new ArrayList<Artifact>( getArtifacts().size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( a.getArtifactHandler().isAddedToClasspath() )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) || Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    list.add( a );
                }
            }
        }
        return list;
    }

    public List<Dependency> getCompileDependencies()
    {
        Set<Artifact> artifacts = getArtifacts();

        if ( ( artifacts == null ) || artifacts.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<Dependency> list = new ArrayList<Dependency>( artifacts.size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: let the scope handler deal with this
            if ( Artifact.SCOPE_COMPILE.equals( a.getScope() ) || Artifact.SCOPE_PROVIDED.equals( a.getScope() ) || Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
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

    public List<String> getTestClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List<String> list = new ArrayList<String>( getArtifacts().size() + 2 );

        list.add( getBuild().getTestOutputDirectory() );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactHandler().isAddedToClasspath() )
            {
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

    public List<Artifact> getTestArtifacts()
    {
        List<Artifact> list = new ArrayList<Artifact>( getArtifacts().size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( a.getArtifactHandler().isAddedToClasspath() )
            {
                list.add( a );
            }
        }
        return list;
    }

    public List<Dependency> getTestDependencies()
    {
        Set<Artifact> artifacts = getArtifacts();

        if ( ( artifacts == null ) || artifacts.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<Dependency> list = new ArrayList<Dependency>( artifacts.size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

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

    public List<String> getRuntimeClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List<String> list = new ArrayList<String>( getArtifacts().size() + 1 );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactHandler().isAddedToClasspath() )
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

    public List<Artifact> getRuntimeArtifacts()
    {
        List<Artifact> list = new ArrayList<Artifact>( getArtifacts().size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( a.getArtifactHandler().isAddedToClasspath() )
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

    public List<Dependency> getRuntimeDependencies()
    {
        Set<Artifact> artifacts = getArtifacts();

        if ( ( artifacts == null ) || artifacts.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<Dependency> list = new ArrayList<Dependency>( artifacts.size() );

        for ( Iterator<Artifact> i = artifacts.iterator(); i.hasNext(); )
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

    public List<String> getSystemClasspathElements()
        throws DependencyResolutionRequiredException
    {
        List<String> list = new ArrayList<String>( getArtifacts().size() );

        list.add( getBuild().getOutputDirectory() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            if ( a.getArtifactHandler().isAddedToClasspath() )
            {
                // TODO: let the scope handler deal with this
                if ( Artifact.SCOPE_SYSTEM.equals( a.getScope() ) )
                {
                    addArtifactPath( a, list );
                }
            }
        }
        return list;
    }

    public List<Artifact> getSystemArtifacts()
    {
        List<Artifact> list = new ArrayList<Artifact>( getArtifacts().size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
        {
            Artifact a = (Artifact) i.next();

            // TODO: classpath check doesn't belong here - that's the other method
            if ( a.getArtifactHandler().isAddedToClasspath() )
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

    public List<Dependency> getSystemDependencies()
    {
        Set<Artifact> artifacts = getArtifacts();

        if ( ( artifacts == null ) || artifacts.isEmpty() )
        {
            return Collections.emptyList();
        }

        List<Dependency> list = new ArrayList<Dependency>( artifacts.size() );

        for ( Iterator<Artifact> i = getArtifacts().iterator(); i.hasNext(); )
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

    // ----------------------------------------------------------------------
    // Delegate to the model
    // ----------------------------------------------------------------------

    public void setModelVersion( String pomVersion )
    {
        getModel().setModelVersion( pomVersion );
    }

    public String getModelVersion()
    {
        return getModel().getModelVersion();
    }

    public String getId()
    {
        return getModel().getId();
    }

    public void setGroupId( String groupId )
    {
        getModel().setGroupId( groupId );
    }

    public String getGroupId()
    {
        String groupId = getModel().getGroupId();

        if ( ( groupId == null ) && ( getModel().getParent() != null ) )
        {
            groupId = getModel().getParent().getGroupId();
        }

        return groupId;
    }

    public void setArtifactId( String artifactId )
    {
        getModel().setArtifactId( artifactId );
    }

    public String getArtifactId()
    {
        return getModel().getArtifactId();
    }

    public void setName( String name )
    {
        getModel().setName( name );
    }

    public String getName()
    {
        // TODO: this should not be allowed to be null.
        if ( getModel().getName() != null )
        {
            return getModel().getName();
        }
        else
        {
            return "Unnamed - " + getId();
        }
    }

    public void setVersion( String version )
    {
        getModel().setVersion( version );
    }

    public String getVersion()
    {
        String version = getModel().getVersion();

        if ( ( version == null ) && ( getModel().getParent() != null ) )
        {
            version = getModel().getParent().getVersion();
        }

        return version;
    }

    public String getPackaging()
    {
        return getModel().getPackaging();
    }

    public void setPackaging( String packaging )
    {
        getModel().setPackaging( packaging );
    }

    public void setInceptionYear( String inceptionYear )
    {
        getModel().setInceptionYear( inceptionYear );
    }

    public String getInceptionYear()
    {
        return getModel().getInceptionYear();
    }

    public void setUrl( String url )
    {
        getModel().setUrl( url );
    }

    public String getUrl()
    {
        return getModel().getUrl();
    }

    public Prerequisites getPrerequisites()
    {
        return getModel().getPrerequisites();
    }

    public void setIssueManagement( IssueManagement issueManagement )
    {
        getModel().setIssueManagement( issueManagement );
    }

    public CiManagement getCiManagement()
    {
        return getModel().getCiManagement();
    }

    public void setCiManagement( CiManagement ciManagement )
    {
        getModel().setCiManagement( ciManagement );
    }

    public IssueManagement getIssueManagement()
    {
        return getModel().getIssueManagement();
    }

    public void setDistributionManagement( DistributionManagement distributionManagement )
    {
        getModel().setDistributionManagement( distributionManagement );
    }

    public DistributionManagement getDistributionManagement()
    {
        return getModel().getDistributionManagement();
    }

    public void setDescription( String description )
    {
        getModel().setDescription( description );
    }

    public String getDescription()
    {
        return getModel().getDescription();
    }

    public void setOrganization( Organization organization )
    {
        getModel().setOrganization( organization );
    }

    public Organization getOrganization()
    {
        return getModel().getOrganization();
    }

    public void setScm( Scm scm )
    {
        getModel().setScm( scm );
    }

    public Scm getScm()
    {
        return getModel().getScm();
    }

    public void setMailingLists( List<MailingList> mailingLists )
    {
        getModel().setMailingLists( mailingLists );
    }

    public List<MailingList> getMailingLists()
    {
        return getModel().getMailingLists();
    }

    public void addMailingList( MailingList mailingList )
    {
        getModel().addMailingList( mailingList );
    }

    public void setDevelopers( List<Developer> developers )
    {
        getModel().setDevelopers( developers );
    }

    public List<Developer> getDevelopers()
    {
        return getModel().getDevelopers();
    }

    public void addDeveloper( Developer developer )
    {
        getModel().addDeveloper( developer );
    }

    public void setContributors( List<Contributor> contributors )
    {
        getModel().setContributors( contributors );
    }

    public List<Contributor> getContributors()
    {
        return getModel().getContributors();
    }

    public void addContributor( Contributor contributor )
    {
        getModel().addContributor( contributor );
    }

    public void setBuild( Build build )
    {
        getModel().setBuild( build );
    }

    public Build getBuild()
    {
        return getModelBuild();
    }

    public List<Resource> getResources()
    {
        return getBuild().getResources();
    }

    public List<Resource> getTestResources()
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
        getModel().setReporting( reporting );
    }

    public Reporting getReporting()
    {
        return getModel().getReporting();
    }

    public void setLicenses( List<License> licenses )
    {
        getModel().setLicenses( licenses );
    }

    public List<License> getLicenses()
    {
        return getModel().getLicenses();
    }

    public void addLicense( License license )
    {
        getModel().addLicense( license );
    }

    public void setArtifacts( Set<Artifact> artifacts )
    {
        this.artifacts = artifacts;

        // flush the calculated artifactMap
        artifactMap = null;
    }

    /**
     * All dependencies that this project has, including transitive ones. Contents are lazily
     * populated, so depending on what phases have run dependencies in some scopes won't be
     * included. eg. if only compile phase has run, dependencies with scope test won't be included.
     * 
     * @return {@link Set} &lt; {@link Artifact} >
     * @see #getDependencyArtifacts() to get only direct dependencies
     */
    public Set<Artifact> getArtifacts()
    {
        return artifacts == null ? Collections.<Artifact> emptySet() : artifacts;
    }

    public Map<String, Artifact> getArtifactMap()
    {
        if ( artifactMap == null )
        {
            artifactMap = ArtifactUtils.artifactMapByVersionlessId( getArtifacts() );
        }
        return artifactMap;
    }

    public Set<Artifact> getPluginArtifacts()
    {
        if ( pluginArtifacts != null )
        {
            return pluginArtifacts;
        }
        pluginArtifacts = new HashSet<Artifact>();
        if ( repositorySystem != null )
        {
            List<Plugin> plugins = getBuildPlugins();
            for ( Iterator<Plugin> i = plugins.iterator(); i.hasNext(); )
            {
                Plugin p = (Plugin) i.next();

                String version;
                if ( StringUtils.isEmpty( p.getVersion() ) )
                {
                    version = "RELEASE";
                }
                else
                {
                    version = p.getVersion();
                }

                Artifact artifact = repositorySystem.createPluginArtifact( p.getGroupId(), p.getArtifactId(), version );

                if ( artifact == null )
                {
                    return pluginArtifacts;
                }
                else
                {
                    pluginArtifacts.add( artifact );
                }
            }
        }
        pluginArtifactMap = null;
        return pluginArtifacts;
    }

    public Map<String, Artifact> getPluginArtifactMap()
    {
        pluginArtifactMap = ArtifactUtils.artifactMapByVersionlessId( getPluginArtifacts() );
        return pluginArtifactMap;
    }

    public void setReportArtifacts( Set<Artifact> reportArtifacts )
    {
        this.reportArtifacts = reportArtifacts;

        reportArtifactMap = null;
    }

    public Set<Artifact> getReportArtifacts()
    {
        if ( reportArtifacts != null )
        {
            return reportArtifacts;
        }

        reportArtifacts = new HashSet<Artifact>();
        List<ReportPlugin> reports = getReportPlugins();
        if ( reports != null )
        {
            for ( Iterator<ReportPlugin> i = reports.iterator(); i.hasNext(); )
            {
                ReportPlugin p = (ReportPlugin) i.next();

                String version;
                if ( StringUtils.isEmpty( p.getVersion() ) )
                {
                    version = "RELEASE";
                }
                else
                {
                    version = p.getVersion();
                }

                Artifact artifact = repositorySystem.createPluginArtifact( p.getGroupId(), p.getArtifactId(), version );

                if ( artifact != null )
                {
                    reportArtifacts.add( artifact );
                }
            }
        }
        reportArtifactMap = null;
        return reportArtifacts;
    }

    public Map<String, Artifact> getReportArtifactMap()
    {
        if ( reportArtifactMap == null )
        {
            reportArtifactMap = ArtifactUtils.artifactMapByVersionlessId( getReportArtifacts() );
        }

        return reportArtifactMap;
    }

    public void setExtensionArtifacts( Set<Artifact> extensionArtifacts )
    {
        this.extensionArtifacts = extensionArtifacts;

        extensionArtifactMap = null;
    }

    public Set<Artifact> getExtensionArtifacts()
    {
        if ( extensionArtifacts != null )
        {
            return extensionArtifacts;
        }
        extensionArtifacts = new HashSet<Artifact>();
        List<Extension> extensions = getBuildExtensions();
        if ( extensions != null )
        {
            for ( Iterator<Extension> i = extensions.iterator(); i.hasNext(); )
            {
                Extension ext = i.next();

                String version;
                if ( StringUtils.isEmpty( ext.getVersion() ) )
                {
                    version = "RELEASE";
                }
                else
                {
                    version = ext.getVersion();
                }

                Artifact artifact = repositorySystem.createExtensionArtifact( ext.getGroupId(), ext.getArtifactId(), version );

                if ( artifact != null )
                {
                    extensionArtifacts.add( artifact );
                }
            }
        }
        extensionArtifactMap = null;
        return extensionArtifacts;
    }

    public Map<String, Artifact> getExtensionArtifactMap()
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
        if ( parentArtifact == null && model.getParent() != null )
        {
            Parent p = model.getParent();
            parentArtifact = repositorySystem.createParentArtifact( p.getGroupId(), p.getArtifactId(), p.getVersion() );
        }
        return parentArtifact;
    }

    public List<Repository> getRepositories()
    {
        return getModel().getRepositories();
    }

    // ----------------------------------------------------------------------
    // Plugins
    // ----------------------------------------------------------------------

    public List<ReportPlugin> getReportPlugins()
    {
        if ( getModel().getReporting() == null )
        {
            return Collections.emptyList();
        }
        return getModel().getReporting().getPlugins();

    }

    public List<Plugin> getBuildPlugins()
    {
        if ( getModel().getBuild() == null )
        {
            return Collections.emptyList();
        }
        return getModel().getBuild().getPlugins();
    }

    public List<String> getModules()
    {
        return getModel().getModules();
    }

    public PluginManagement getPluginManagement()
    {
        PluginManagement pluginMgmt = null;

        Build build = getModel().getBuild();
        if ( build != null )
        {
            pluginMgmt = build.getPluginManagement();
        }

        return pluginMgmt;
    }

    private Build getModelBuild()
    {
        Build build = getModel().getBuild();

        if ( build == null )
        {
            build = new Build();

            getModel().setBuild( build );
        }

        return build;
    }

    public void addPlugin( Plugin plugin )
    {
        Build build = getModelBuild();

        if ( !build.getPluginsAsMap().containsKey( plugin.getKey() ) )
        {
            injectPluginManagementInfo( plugin );

            build.addPlugin( plugin );

            build.flushPluginMap();
        }
    }

    public void injectPluginManagementInfo( Plugin plugin )
    {
        PluginManagement pm = getModelBuild().getPluginManagement();

        if ( pm != null )
        {
            Map<String, Plugin> pmByKey = pm.getPluginsAsMap();

            String pluginKey = plugin.getKey();

            if ( ( pmByKey != null ) && pmByKey.containsKey( pluginKey ) )
            {
                Plugin pmPlugin = pmByKey.get( pluginKey );

                ModelUtils.mergePluginDefinitions( plugin, pmPlugin, false );
            }
        }
    }

    public List<MavenProject> getCollectedProjects()
    {
        return collectedProjects;
    }

    public void setCollectedProjects( List<MavenProject> collectedProjects )
    {
        this.collectedProjects = collectedProjects;
    }

    public void setPluginArtifactRepositories( List<ArtifactRepository> pluginArtifactRepositories )
    {
        this.pluginArtifactRepositories = pluginArtifactRepositories;
    }

    /**
     * @return a list of ArtifactRepository objects constructed from the Repository objects returned
     *         by getPluginRepositories.
     */
    public List<ArtifactRepository> getPluginArtifactRepositories()
    {
        return getRemoteArtifactRepositories();
    }

    public ArtifactRepository getDistributionManagementArtifactRepository()
    {
        return getArtifact().isSnapshot() && ( getSnapshotArtifactRepository() != null ) ? getSnapshotArtifactRepository() : getReleaseArtifactRepository();
    }

    public List<Repository> getPluginRepositories()
    {
        //        return model.getPluginRepositories();
        return getModel().getRepositories();
    }

    public void setActiveProfiles( List<Profile> activeProfiles )
    {
        this.activeProfiles.addAll( activeProfiles );
    }

    public List<Profile> getActiveProfiles()
    {
        return activeProfiles;
    }

    public void addAttachedArtifact( Artifact artifact )
        throws DuplicateArtifactAttachmentException
    {
        List<Artifact> attachedArtifacts = getAttachedArtifacts();

        if ( attachedArtifacts.contains( artifact ) )
        {
            //should add logger to this class:
            System.out.println( "[Warning] Duplicate artifact: " + artifact.toString() );
            return;
            //throw new DuplicateArtifactAttachmentException( this, artifact );
        }

        getAttachedArtifacts().add( artifact );
    }

    public List<Artifact> getAttachedArtifacts()
    {
        if ( attachedArtifacts == null )
        {
            attachedArtifacts = new ArrayList<Artifact>();
        }
        return attachedArtifacts;
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
            for ( Iterator<ReportPlugin> iterator = getReportPlugins().iterator(); iterator.hasNext(); )
            {
                ReportPlugin plugin = iterator.next();

                if ( pluginGroupId.equals( plugin.getGroupId() ) && pluginArtifactId.equals( plugin.getArtifactId() ) )
                {
                    dom = (Xpp3Dom) plugin.getConfiguration();

                    if ( reportSetId != null )
                    {
                        ReportSet reportSet = plugin.getReportSetsAsMap().get( reportSetId );
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
        return ( executionProject == null ? this : executionProject );
    }

    public void setExecutionProject( MavenProject executionProject )
    {
        if ( this.executionProject != null )
        {
            previousExecutionProjects.push( this.executionProject );
        }

        this.executionProject = executionProject;
    }

    /**
     * Direct dependencies that this project has.
     * 
     * @return {@link Set} &lt; {@link Artifact} >
     * @see #getArtifacts() to get all transitive dependencies
     */
    public Set<Artifact> getDependencyArtifacts()
    {
        return dependencyArtifacts;
    }

    public void setDependencyArtifacts( Set<Artifact> dependencyArtifacts )
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

    public void setManagedVersionMap( Map<String, Artifact> map )
    {
        managedVersionMap = map;
    }

    public Map<String, Artifact> getManagedVersionMap()
    {
        if ( managedVersionMap != null )
        {
            return managedVersionMap;
        }

        Map<String, Artifact> map = null;
        if ( repositorySystem != null )
        {

            List<Dependency> deps;
            DependencyManagement dependencyManagement = getDependencyManagement();
            if ( ( dependencyManagement != null ) && ( ( deps = dependencyManagement.getDependencies() ) != null ) && ( deps.size() > 0 ) )
            {
                map = new ManagedVersionMap( map );
                for ( Iterator<Dependency> i = dependencyManagement.getDependencies().iterator(); i.hasNext(); )
                {
                    Dependency d = i.next();

                    Artifact artifact = repositorySystem.createDependencyArtifact( d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getType(), d.getClassifier(), d.getScope(), d.isOptional() );

                    if ( artifact == null )
                    {
                        map = Collections.emptyMap();
                    }

                    if ( Artifact.SCOPE_SYSTEM.equals( d.getScope() ) && ( d.getSystemPath() != null ) )
                    {
                        artifact.setFile( new File( d.getSystemPath() ) );
                    }

                    // If the dependencyManagement section listed exclusions,
                    // add them to the managed artifacts here so that transitive
                    // dependencies will be excluded if necessary.

                    if ( ( null != d.getExclusions() ) && !d.getExclusions().isEmpty() )
                    {
                        List<String> exclusions = new ArrayList<String>();

                        for ( Iterator<Exclusion> j = d.getExclusions().iterator(); j.hasNext(); )
                        {
                            Exclusion e = j.next();

                            exclusions.add( e.getGroupId() + ":" + e.getArtifactId() );
                        }

                        ExcludesArtifactFilter eaf = new ExcludesArtifactFilter( exclusions );

                        artifact.setDependencyFilter( eaf );
                    }
                    else
                    {
                        artifact.setDependencyFilter( null );
                    }

                    map.put( d.getManagementKey(), artifact );
                }
            }
            else
            {
                map = Collections.emptyMap();
            }
        }
        managedVersionMap = map;
        return managedVersionMap;
    }

    @Override
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

    @Override
    public int hashCode()
    {
        return getId().hashCode();
    }

    public List<Extension> getBuildExtensions()
    {
        Build build = getBuild();
        if ( ( build == null ) || ( build.getExtensions() == null ) )
        {
            return Collections.emptyList();
        }
        else
        {
            return build.getExtensions();
        }
    }

    /**
     * @return {@link Set} &lt; {@link Artifact} >
     * @todo the lazy initialisation of this makes me uneasy.
     */
    public Set<Artifact> createArtifacts( ArtifactFactory artifactFactory, String inheritedScope, ArtifactFilter dependencyFilter )
        throws InvalidDependencyVersionException
    {
        try
        {
            return repositorySystem.createArtifacts( getDependencies(), inheritedScope, dependencyFilter, this );
        }
        catch ( VersionNotFoundException e )
        {
            throw new InvalidDependencyVersionException( e.getProjectId(), e.getDependency(), e.getPomFile(), e.getCauseException() );
        }
    }

    public void addProjectReference( MavenProject project )
    {
        projectReferences.put( getProjectReferenceId( project.getGroupId(), project.getArtifactId(), project.getVersion() ), project );
    }

    /**
     * @deprecated Use MavenProjectHelper.attachArtifact(..) instead.
     */
    @Deprecated
    public void attachArtifact( String type, String classifier, File file )
    {
    }

    public Properties getProperties()
    {
        return getModel().getProperties();
    }

    public List<String> getFilters()
    {
        return getBuild().getFilters();
    }

    public Map<String, MavenProject> getProjectReferences()
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
    
    public Artifact find( Artifact artifact )
    {
        return replaceWithActiveArtifact( artifact );
    }

    public Artifact replaceWithActiveArtifact( Artifact pluginArtifact )
    {
        if ( ( getProjectReferences() != null ) && !getProjectReferences().isEmpty() )
        {
            String refId = getProjectReferenceId( pluginArtifact.getGroupId(), pluginArtifact.getArtifactId(), pluginArtifact.getVersion() );
            MavenProject ref = getProjectReferences().get( refId );
            if ( ref != null )
            {
                if ( ref.getArtifact() != null
                    && ref.getArtifact().getDependencyConflictId().equals( pluginArtifact.getDependencyConflictId() ) )
                {
                    // if the project artifact doesn't exist, don't use it. We haven't built that far.
                    if ( ref.getArtifact().getFile() != null && ref.getArtifact().getFile().exists() )
                    {
                        // FIXME: Why aren't we using project.getArtifact() for the second parameter here??
                        Artifact resultArtifact = new ActiveProjectArtifact( ref, pluginArtifact );
                        return resultArtifact;
                    }
                    else
                    {
                        logMissingSiblingProjectArtifact( pluginArtifact );
                    }
                }

                Artifact attached = findMatchingArtifact( ref.getAttachedArtifacts(), pluginArtifact );
                if ( attached != null )
                {
                    if ( attached.getFile() != null && attached.getFile().exists() )
                    {
                        Artifact resultArtifact = ArtifactUtils.copyArtifact( attached );
                        resultArtifact.setScope( pluginArtifact.getScope() );
                        return resultArtifact;
                    }
                    else
                    {
                        logMissingSiblingProjectArtifact( pluginArtifact );
                    }
                }

                /**
                 * Patch/workaround for: MNG-2871
                 * 
                 * We want to use orginal artifact (packaging:ejb) when we are resolving ejb-client
                 * package and we didn't manage to find attached to project one.
                 * 
                 * The scenario is such that somebody run "mvn test" in composity project, and
                 * ejb-client.jar will not be attached to ejb.jar (because it is done in package
                 * phase)
                 * 
                 * We prefer in such a case use orginal sources (of ejb.jar) instead of failure
                 */
                if ( ( ref.getArtifactId().equals( pluginArtifact.getArtifactId() ) ) && ( ref.getGroupId().equals( pluginArtifact.getGroupId() ) ) && ( ref.getArtifact().getType().equals( "ejb" ) )
                    && ( pluginArtifact.getType().equals( "ejb-client" ) ) && ( ( ref.getArtifact().getFile() != null ) && ref.getArtifact().getFile().exists() ) )
                {
                    pluginArtifact = new ActiveProjectArtifact( ref, pluginArtifact );
                    return pluginArtifact;
                }
            }
        }
        return pluginArtifact;
    }

    /**
     * Tries to resolve the specified artifact from the given collection of attached project artifacts.
     * 
     * @param artifacts The attached artifacts, may be <code>null</code>.
     * @param requestedArtifact The artifact to resolve, must not be <code>null</code>.
     * @return The matching artifact or <code>null</code> if not found.
     */
    private Artifact findMatchingArtifact( List<Artifact> artifacts, Artifact requestedArtifact )
    {
        if ( artifacts != null && !artifacts.isEmpty() )
        {
            // first try matching by dependency conflict id
            String requestedId = requestedArtifact.getDependencyConflictId();
            for ( Artifact artifact : artifacts )
            {
                if ( requestedId.equals( artifact.getDependencyConflictId() ) )
                {
                    return artifact;
                }
            }

            // next try matching by repository conflict id
            requestedId = getRepositoryConflictId( requestedArtifact );
            for ( Artifact artifact : artifacts )
            {
                if ( requestedId.equals( getRepositoryConflictId( artifact ) ) )
                {
                    return artifact;
                }
            }
        }

        return null;
    }

    /**
     * Gets the repository conflict id of the specified artifact. Unlike the dependency conflict id, the repository
     * conflict id uses the artifact file extension instead of the artifact type. Hence, the repository conflict id more
     * closely reflects the identity of artifacts as perceived by a repository.
     * 
     * @param artifact The artifact, must not be <code>null</code>.
     * @return The repository conflict id, never <code>null</code>.
     */
    private String getRepositoryConflictId( Artifact artifact )
    {
        StringBuffer buffer = new StringBuffer( 128 );
        buffer.append( artifact.getGroupId() );
        buffer.append( ':' ).append( artifact.getArtifactId() );
        if ( artifact.getArtifactHandler() != null )
        {
            buffer.append( ':' ).append( artifact.getArtifactHandler().getExtension() );
        }
        else
        {
            buffer.append( ':' ).append( artifact.getType() );
        }
        if ( artifact.hasClassifier() )
        {
            buffer.append( ':' ).append( artifact.getClassifier() );
        }
        return buffer.toString();
    }

    private void logMissingSiblingProjectArtifact( Artifact artifact )
    {
        /* TODO
        if ( logger == null )
        {
            return;
        }
        
        StringBuffer message = new StringBuffer();
        message.append( "A dependency of the current project (or of one the plugins used in its build) was found in the reactor, " );
        message.append( "\nbut had not been built at the time it was requested. It will be resolved from the repository instead." );
        message.append( "\n\nCurrent Project: " ).append( getName() );
        message.append( "\nRequested Dependency: " ).append( artifact.getId() );
        message.append( "\n\nNOTE: You may need to run this build to the 'compile' lifecycle phase, or farther, in order to build the dependency artifact." );
        message.append( "\n" );
        
        logger.warn( message.toString() );
        */
    }

    public void clearExecutionProject()
    {
        if ( !previousExecutionProjects.isEmpty() )
        {
            executionProject = (MavenProject) previousExecutionProjects.pop();
        }
        else
        {
            executionProject = null;
        }
    }

    public Plugin getPlugin( String pluginKey )
    {
        return (Plugin) getBuild().getPluginsAsMap().get( pluginKey );
    }

    /**
     * Default toString
     */
    @Override
    public String toString()
    {
        StringBuffer sb = new StringBuffer( 30 );
        sb.append( "MavenProject: " );
        sb.append( getGroupId() );
        sb.append( ":" );
        sb.append( getArtifactId() );
        sb.append( ":" );
        sb.append( getVersion() );
        sb.append( " @ " );

        try
        {
            sb.append( getFile().getPath() );
        }
        catch ( NullPointerException e )
        {
            //don't log it.
        }

        return sb.toString();
    }

    public void writeModel( Writer writer )
        throws IOException
    {
        MavenXpp3Writer pomWriter = new MavenXpp3Writer();
        pomWriter.write( writer, getModel() );
    }

    /**
     * @throws CloneNotSupportedException
     * @since 2.0.9
     */
    @Override
    public Object clone()
        throws CloneNotSupportedException
    {
        MavenProject clone = (MavenProject) super.clone();
        clone.deepCopy( this );
        return clone;
    }

    protected void setModel( Model model )
    {
        this.model = model;
    }

    protected void setAttachedArtifacts( List<Artifact> attachedArtifacts )
    {
        this.attachedArtifacts = attachedArtifacts;
    }

    protected void setCompileSourceRoots( List<String> compileSourceRoots )
    {
        this.compileSourceRoots = compileSourceRoots;
    }

    protected void setTestCompileSourceRoots( List<String> testCompileSourceRoots )
    {
        this.testCompileSourceRoots = testCompileSourceRoots;
    }

    protected void setScriptSourceRoots( List<String> scriptSourceRoots )
    {
        this.scriptSourceRoots = scriptSourceRoots;
    }

    protected ArtifactRepository getReleaseArtifactRepository()
    {
        return releaseArtifactRepository;
    }

    protected ArtifactRepository getSnapshotArtifactRepository()
    {
        return snapshotArtifactRepository;
    }

    private void deepCopy( MavenProject project )
    {
        // disown the parent

        // copy fields
        setFile( project.getFile() );

        // don't need a deep copy, they don't get modified or added/removed to/from - but make them unmodifiable to be
        // sure!
        if ( project.getDependencyArtifacts() != null )
        {
            setDependencyArtifacts( Collections.unmodifiableSet( project.getDependencyArtifacts() ) );
        }

        if ( project.getArtifacts() != null )
        {
            setArtifacts( Collections.unmodifiableSet( project.getArtifacts() ) );
        }

        if ( project.getParentFile() != null )
        {
            parentFile = new File( project.getParentFile().getAbsolutePath() );
        }

        if ( project.getReportArtifacts() != null )
        {
            setReportArtifacts( Collections.unmodifiableSet( project.getReportArtifacts() ) );
        }

        if ( project.getExtensionArtifacts() != null )
        {
            setExtensionArtifacts( Collections.unmodifiableSet( project.getExtensionArtifacts() ) );
        }

        setParentArtifact( ( project.getParentArtifact() ) );

        if ( project.getRemoteArtifactRepositories() != null )
        {
            setRemoteArtifactRepositories( Collections.unmodifiableList( project.getRemoteArtifactRepositories() ) );
        }

        if ( project.getPluginArtifactRepositories() != null )
        {
            setPluginArtifactRepositories( ( Collections.unmodifiableList( project.getPluginArtifactRepositories() ) ) );
        }

        if ( project.getCollectedProjects() != null )
        {
            setCollectedProjects( ( Collections.unmodifiableList( project.getCollectedProjects() ) ) );
        }

        if ( project.getActiveProfiles() != null )
        {
            setActiveProfiles( ( Collections.unmodifiableList( project.getActiveProfiles() ) ) );
        }

        if ( project.getAttachedArtifacts() != null )
        {
            // clone properties modifyable by plugins in a forked lifecycle
            setAttachedArtifacts( new ArrayList<Artifact>( project.getAttachedArtifacts() ) );
        }

        if ( project.getCompileSourceRoots() != null )
        {
            // clone source roots
            setCompileSourceRoots( ( new ArrayList<String>( project.getCompileSourceRoots() ) ) );
        }

        if ( project.getTestCompileSourceRoots() != null )
        {
            setTestCompileSourceRoots( ( new ArrayList<String>( project.getTestCompileSourceRoots() ) ) );
        }

        if ( project.getScriptSourceRoots() != null )
        {
            setScriptSourceRoots( ( new ArrayList<String>( project.getScriptSourceRoots() ) ) );
        }

        /*
         * TODO: This is temporary solution for the failure of IT mng-0471. When StartForkedExecutionMojo clones the
         * project it really needs a deep copy of the model to make sure manipulations to the project/model during the
         * forked execution don't pollute the main execution. It's not clear to me right now whether manipulations to
         * the model itself should just be prohibited (say be means of UnsupportedOperationExceptions) and only have the
         * project be mutable. If we allow model updates like in 2.x, the code below should better be replaced with the
         * original cloning code from ModelUtils.
         */
        if ( project.getModel() != null )
        {
            try
            {
                StringWriter modelWriter = new StringWriter( 1024 * 10 );
                project.writeModel( modelWriter );
                MavenXpp3Reader parser = new MavenXpp3Reader();
                setModel( parser.read( new StringReader( modelWriter.toString() ) ) );
            }
            catch ( Exception e )
            {
                throw new IllegalStateException( "in-memory cloning failed", e );
            }
        }

        if ( project.getOriginalModel() != null )
        {
            setOriginalModel( project.getOriginalModel() );
        }

        setExecutionRoot( project.isExecutionRoot() );

        if ( project.getArtifact() != null )
        {
            setArtifact( ArtifactUtils.copyArtifact( project.getArtifact() ) );
        }

        if ( project.getManagedVersionMap() != null )
        {
            setManagedVersionMap( new ManagedVersionMap( project.getManagedVersionMap() ) );
        }

        if ( project.getReleaseArtifactRepository() != null )
        {
            setReleaseArtifactRepository( project.getReleaseArtifactRepository() );
        }

        if ( project.getSnapshotArtifactRepository() != null )
        {
            setSnapshotArtifactRepository( project.getSnapshotArtifactRepository() );
        }
    }

    private void addArtifactPath( Artifact a, List<String> list )
        throws DependencyResolutionRequiredException
    {
        File file = a.getFile();
        if ( file != null )
        {
            list.add( file.getPath() );
        }
        else
        {
            String refId = getProjectReferenceId( a.getGroupId(), a.getArtifactId(), a.getVersion() );
            MavenProject project = (MavenProject) projectReferences.get( refId );

            boolean projectDirFound = false;
            if ( project != null )
            {
                if ( "test-jar".equals( a.getType() ) )
                {
                    File testOutputDir = new File( project.getBuild().getTestOutputDirectory() );
                    if ( testOutputDir.exists() )
                    {
                        list.add( testOutputDir.getAbsolutePath() );
                        projectDirFound = true;
                    }
                }
                else
                {
                    list.add( project.getBuild().getOutputDirectory() );
                    projectDirFound = true;
                }
            }
            if ( !projectDirFound )
            {
                throw new DependencyResolutionRequiredException( a );
            }
        }
    }

    private static String getProjectReferenceId( String groupId, String artifactId, String version )
    {
        return groupId + ":" + artifactId + ":" + version;
    }
}
