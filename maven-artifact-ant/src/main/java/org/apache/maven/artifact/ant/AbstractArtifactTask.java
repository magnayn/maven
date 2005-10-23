package org.apache.maven.artifact.ant;

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
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.apache.maven.usability.diagnostics.ErrorDiagnostics;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.classworlds.DuplicateRealmException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.embed.Embedder;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Base class for artifact tasks.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public abstract class AbstractArtifactTask
    extends Task
{
    private Settings settings;

    private Embedder embedder;

    private Pom pom;

    private String pomRefId;

    private LocalRepository localRepository;

    protected ArtifactRepository createLocalArtifactRepository()
    {
        if ( localRepository == null )
        {
            localRepository = getDefaultLocalRepository();
        }

        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, localRepository.getLayout() );

        return new DefaultArtifactRepository( "local", "file://" + localRepository.getLocation(), repositoryLayout );
    }

    protected ArtifactRepository createRemoteArtifactRepository( RemoteRepository repository )
    {
        ArtifactRepositoryLayout repositoryLayout =
            (ArtifactRepositoryLayout) lookup( ArtifactRepositoryLayout.ROLE, repository.getLayout() );

        WagonManager manager = (WagonManager) lookup( WagonManager.ROLE );

        Authentication authentication = repository.getAuthentication();
        if ( authentication != null )
        {
            manager.addAuthenticationInfo( "remote", authentication.getUserName(), authentication.getPassword(),
                                           authentication.getPrivateKey(), authentication.getPassphrase() );
        }

        Proxy proxy = repository.getProxy();
        if ( proxy != null )
        {
            manager.addProxy( proxy.getType(), proxy.getHost(), proxy.getPort(), proxy.getUserName(),
                              proxy.getPassword(), proxy.getNonProxyHosts() );
        }

        ArtifactRepositoryFactory repositoryFactory = null;

        ArtifactRepository artifactRepository;

        try
        {
            repositoryFactory = (ArtifactRepositoryFactory) lookup( ArtifactRepositoryFactory.ROLE );

            ArtifactRepositoryPolicy snapshots = buildArtifactRepositoryPolicy( repository.getSnapshots() );
            ArtifactRepositoryPolicy releases = buildArtifactRepositoryPolicy( repository.getReleases() );

            artifactRepository = repositoryFactory.createArtifactRepository( "remote", repository.getUrl(),
                                                                             repositoryLayout, snapshots, releases );
        }
        finally
        {
            try
            {
                getEmbedder().release( repositoryFactory );
            }
            catch ( ComponentLifecycleException e )
            {
                // TODO: Warn the user, or not?
            }
        }

        return artifactRepository;
    }

    private static ArtifactRepositoryPolicy buildArtifactRepositoryPolicy( RepositoryPolicy policy )
    {
        boolean enabled = true;
        String updatePolicy = null;
        String checksumPolicy = null;

        if ( policy != null )
        {
            enabled = policy.isEnabled();
            if ( policy.getUpdatePolicy() != null )
            {
                updatePolicy = policy.getUpdatePolicy();
            }
            if ( policy.getChecksumPolicy() != null )
            {
                checksumPolicy = policy.getChecksumPolicy();
            }
        }

        return new ArtifactRepositoryPolicy( enabled, updatePolicy, checksumPolicy );
    }

    protected LocalRepository getDefaultLocalRepository()
    {
        Settings settings = getSettings();
        LocalRepository localRepository = new LocalRepository();
        localRepository.setLocation( new File( settings.getLocalRepository() ) );
        return localRepository;
    }

    protected synchronized Settings getSettings()
    {
        if ( settings == null )
        {
            settings = new Settings();

            File settingsFile = new File( System.getProperty( "user.home" ), ".ant/settings.xml" );
            if ( !settingsFile.exists() )
            {
                settingsFile = new File( System.getProperty( "user.home" ), ".m2/settings.xml" );
            }

            if ( settingsFile.exists() )
            {
                FileReader reader = null;
                try
                {
                    reader = new FileReader( settingsFile );

                    SettingsXpp3Reader modelReader = new SettingsXpp3Reader();

                    settings = modelReader.read( reader );
                }
                catch ( IOException e )
                {
                    log( "Error reading settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage(),
                         Project.MSG_WARN );
                }
                catch ( XmlPullParserException e )
                {
                    log( "Error parsing settings file '" + settingsFile + "' - ignoring. Error was: " + e.getMessage(),
                         Project.MSG_WARN );
                }
                finally
                {
                    IOUtil.close( reader );
                }
            }

            if ( StringUtils.isEmpty( settings.getLocalRepository() ) )
            {
                String location = new File( System.getProperty( "user.home" ), ".m2/repository" ).getAbsolutePath();
                settings.setLocalRepository( location );
            }
        }
        return settings;
    }

    protected RemoteRepository createAntRemoteRepository( org.apache.maven.model.Repository pomRepository )
    {
        RemoteRepository r = createAntRemoteRepositoryBase( pomRepository );

        if ( pomRepository.getSnapshots() != null )
        {
            r.addSnapshots( convertRepositoryPolicy( pomRepository.getSnapshots() ) );
        }
        if ( pomRepository.getReleases() != null )
        {
            r.addReleases( convertRepositoryPolicy( pomRepository.getReleases() ) );
        }

        return r;
    }

    protected RemoteRepository createAntRemoteRepositoryBase( org.apache.maven.model.RepositoryBase pomRepository )
    {
        // TODO: actually, we need to not funnel this through the ant repository - we should pump settings into wagon
        // manager at the start like m2 does, and then match up by repository id
        // As is, this could potentially cause a problem with 2 remote repositories with different authentication info

        RemoteRepository r = new RemoteRepository();
        r.setUrl( pomRepository.getUrl() );
        r.setLayout( pomRepository.getLayout() );

        Server server = getSettings().getServer( pomRepository.getId() );
        if ( server != null )
        {
            r.addAuthentication( new Authentication( server ) );
        }

        org.apache.maven.settings.Proxy proxy = getSettings().getActiveProxy();
        if ( proxy != null )
        {
            r.addProxy( new Proxy( proxy ) );
        }

        Mirror mirror = getSettings().getMirrorOf( pomRepository.getId() );
        if ( mirror != null )
        {
            r.setUrl( mirror.getUrl() );
        }
        return r;
    }

    protected Object lookup( String role )
    {
        try
        {
            return getEmbedder().lookup( role );
        }
        catch ( ComponentLookupException e )
        {
            throw new BuildException( "Unable to find component: " + role, e );
        }
    }

    protected Object lookup( String role, String roleHint )
    {
        try
        {
            return getEmbedder().lookup( role, roleHint );
        }
        catch ( ComponentLookupException e )
        {
            throw new BuildException( "Unable to find component: " + role + "[" + roleHint + "]", e );
        }
    }

    protected static RemoteRepository getDefaultRemoteRepository()
    {
        // TODO: could we utilise the super POM for this?
        RemoteRepository remoteRepository = new RemoteRepository();
        remoteRepository.setUrl( "http://repo1.maven.org/maven2" );
        RepositoryPolicy snapshots = new RepositoryPolicy();
        snapshots.setEnabled( false );
        remoteRepository.addSnapshots( snapshots );
        return remoteRepository;
    }

    protected synchronized Embedder getEmbedder()
    {
        if ( embedder == null )
        {
            embedder = (Embedder) getProject().getReference( Embedder.class.getName() );

            if ( embedder == null )
            {
                embedder = new Embedder();

                try
                {
                    ClassWorld classWorld = new ClassWorld();

                    classWorld.newRealm( "plexus.core", getClass().getClassLoader() );

                    embedder.start( classWorld );
                }
                catch ( PlexusContainerException e )
                {
                    throw new BuildException( "Unable to start embedder", e );
                }
                catch ( DuplicateRealmException e )
                {
                    throw new BuildException( "Unable to create embedder ClassRealm", e );
                }

                getProject().addReference( Embedder.class.getName(), embedder );
            }
        }
        return embedder;
    }

    public Pom buildPom( MavenProjectBuilder projectBuilder, ArtifactRepository localArtifactRepository )
    {
        if ( pomRefId != null && pom != null )
        {
            throw new BuildException( "You cannot specify both a POM element and a pomrefid element" );
        }

        Pom pom = this.pom;
        if ( pomRefId != null )
        {
            pom = (Pom) getProject().getReference( pomRefId );
            if ( pom == null )
            {
                throw new BuildException( "Reference '" + pomRefId + "' was not found." );
            }
        }

        if ( pom != null )
        {
            pom.initialise( projectBuilder, localArtifactRepository );
        }
        return pom;
    }

    protected Pom createDummyPom()
    {
        Model mavenModel = new Model();

        mavenModel.setGroupId( "unspecified" );
        mavenModel.setArtifactId( "unspecified" );
        mavenModel.setVersion( "0.0" );
        mavenModel.setPackaging( "jar" );

        MavenProject mavenProject = new MavenProject( mavenModel );

        Pom pom = new Pom();

        pom.setMavenProject( mavenProject );

        return pom;
    }

    public void diagnoseError( Throwable error )
    {
        try
        {
            ErrorDiagnostics diagnostics = (ErrorDiagnostics) embedder.lookup( ErrorDiagnostics.ROLE );

            StringBuffer message = new StringBuffer();

            message.append( "An error has occurred while processing the Maven artifact tasks.\n" );
            message.append( " Diagnosis:\n\n" );

            message.append( diagnostics.diagnose( error ) );

            message.append( "\n\n" );

            log( message.toString(), Project.MSG_INFO );
        }
        catch ( ComponentLookupException e )
        {
            log( "Failed to retrieve error diagnoser.", Project.MSG_DEBUG );
        }
    }

    public void addPom( Pom pom )
    {
        this.pom = pom;
    }

    public String getPomRefId()
    {
        return pomRefId;
    }

    public void setPomRefId( String pomRefId )
    {
        this.pomRefId = pomRefId;
    }

    public LocalRepository getLocalRepository()
    {
        return localRepository;
    }

    public void addLocalRepository( LocalRepository localRepository )
    {
        this.localRepository = localRepository;
    }

    public void setProfiles( String profiles )
    {
        if ( profiles != null )
        {
            // TODO: not sure this is the best way to do this...
//            System.setProperty( ProfileActivationUtils.ACTIVE_PROFILE_IDS, profiles );
        }
    }

    protected Artifact createArtifact( Pom pom )
    {
        ArtifactFactory factory = (ArtifactFactory) lookup( ArtifactFactory.ROLE );
        // TODO: maybe not strictly correct, while we should enfore that packaging has a type handler of the same id, we don't
        return factory.createBuildArtifact( pom.getGroupId(), pom.getArtifactId(), pom.getVersion(),
                                            pom.getPackaging() );
    }

    private static RepositoryPolicy convertRepositoryPolicy( org.apache.maven.model.RepositoryPolicy pomRepoPolicy )
    {
        RepositoryPolicy policy = new RepositoryPolicy();
        policy.setEnabled( pomRepoPolicy.isEnabled() );
        policy.setUpdatePolicy( pomRepoPolicy.getUpdatePolicy() );
        return policy;
    }

    /**
     * @noinspection RefusedBequest
     */
    public void execute()
    {
        try
        {
            doExecute();
        }
        catch ( BuildException e )
        {
            diagnoseError( e );

            throw e;
        }
    }

    protected abstract void doExecute();
}
