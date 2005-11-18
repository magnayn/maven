package org.apache.maven.archiver;

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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Revision$ $Date$
 */
public class MavenArchiver
{
    private JarArchiver archiver;

    private File archiveFile;

    /**
     * Return a pre-configured manifest
     *
     * @todo Add user attributes list and user groups list
     */
    public Manifest getManifest( MavenProject project, ManifestConfiguration config )
        throws ManifestException, DependencyResolutionRequiredException
    {
        // Added basic entries
        Manifest m = new Manifest();
        Manifest.Attribute buildAttr = new Manifest.Attribute( "Built-By", System.getProperty( "user.name" ) );
        m.addConfiguredAttribute( buildAttr );
        Manifest.Attribute createdAttr = new Manifest.Attribute( "Created-By", "Apache Maven" );
        m.addConfiguredAttribute( createdAttr );

/* TODO: rethink this, it wasn't working
        Artifact projectArtifact = project.getArtifact();

        if ( projectArtifact.isSnapshot() )
        {
            Manifest.Attribute buildNumberAttr = new Manifest.Attribute( "Build-Number", "" +
                project.getSnapshotDeploymentBuildNumber() );
            m.addConfiguredAttribute( buildNumberAttr );
        }

*/
        if ( config.getPackageName() != null )
        {
            Manifest.Attribute packageAttr = new Manifest.Attribute( "Package", config.getPackageName() );
            m.addConfiguredAttribute( packageAttr );
        }

        Manifest.Attribute buildJdkAttr = new Manifest.Attribute( "Build-Jdk", System.getProperty( "java.version" ) );
        m.addConfiguredAttribute( buildJdkAttr );

        if ( config.isAddClasspath() )
        {
            StringBuffer classpath = new StringBuffer();
            List artifacts = project.getRuntimeClasspathElements();
            String classpathPrefix = config.getClasspathPrefix();

            for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
            {
                File f = new File( (String) iter.next() );
                if ( f.isFile() )
                {
                    if ( classpath.length() > 0 )
                    {
                        classpath.append( " " );
                    }

                    classpath.append( classpathPrefix );
                    classpath.append( f.getName() );
                }
            }

            if ( classpath.length() > 0 )
            {
                Manifest.Attribute classpathAttr = new Manifest.Attribute( "Class-Path", classpath.toString() );
                m.addConfiguredAttribute( classpathAttr );
            }
        }

        // Added supplementary entries
        Manifest.Attribute extensionNameAttr = new Manifest.Attribute( "Extension-Name", project.getArtifactId() );
        m.addConfiguredAttribute( extensionNameAttr );

        if ( project.getDescription() != null )
        {
            Manifest.Attribute specificationTitleAttr = new Manifest.Attribute( "Specification-Title",
                                                                                project.getDescription() );
            m.addConfiguredAttribute( specificationTitleAttr );
        }

        if ( project.getOrganization() != null )
        {
            Manifest.Attribute specificationVendor = new Manifest.Attribute( "Specification-Vendor",
                                                                             project.getOrganization().getName() );
            m.addConfiguredAttribute( specificationVendor );
            Manifest.Attribute implementationVendorAttr = new Manifest.Attribute( "Implementation-Vendor",
                                                                                  project.getOrganization().getName() );
            m.addConfiguredAttribute( implementationVendorAttr );
        }

        Manifest.Attribute implementationTitleAttr = new Manifest.Attribute( "Implementation-Title",
                                                                             project.getArtifactId() );
        m.addConfiguredAttribute( implementationTitleAttr );
        Manifest.Attribute implementationVersionAttr = new Manifest.Attribute( "Implementation-Version",
                                                                               project.getVersion() );
        m.addConfiguredAttribute( implementationVersionAttr );

        String mainClass = config.getMainClass();
        if ( mainClass != null && !"".equals( mainClass ) )
        {
            Manifest.Attribute mainClassAttr = new Manifest.Attribute( "Main-Class", mainClass );
            m.addConfiguredAttribute( mainClassAttr );
        }

        // Added extensions
        if ( config.isAddExtensions() )
        {
            StringBuffer extensionsList = new StringBuffer();
            Set artifacts = project.getArtifacts();

            for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                // TODO: type of ejb should be added too?
                if ( "jar".equals( artifact.getType() ) )
                {
                    if ( extensionsList.length() > 0 )
                    {
                        extensionsList.append( " " );
                    }
                    extensionsList.append( artifact.getArtifactId() );
                }
            }

            if ( extensionsList.length() > 0 )
            {
                Manifest.Attribute extensionsListAttr = new Manifest.Attribute( "Extension-List",
                                                                                extensionsList.toString() );
                m.addConfiguredAttribute( extensionsListAttr );
            }

            for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
            {
                Artifact artifact = (Artifact) iter.next();
                if ( "jar".equals( artifact.getType() ) )
                {
                    Manifest.Attribute archExtNameAttr = new Manifest.Attribute(
                        artifact.getArtifactId() + "-Extension-Name", artifact.getArtifactId() );
                    m.addConfiguredAttribute( archExtNameAttr );
                    String name = artifact.getArtifactId() + "-Implementation-Version";
                    Manifest.Attribute archImplVersionAttr = new Manifest.Attribute( name, artifact.getVersion() );
                    m.addConfiguredAttribute( archImplVersionAttr );

                    if ( artifact.getRepository() != null )
                    {
                        // TODO: is this correct
                        name = artifact.getArtifactId() + "-Implementation-URL";
                        String url = artifact.getRepository().getUrl() + "/" + artifact.toString();
                        Manifest.Attribute archImplUrlAttr = new Manifest.Attribute( name, url );
                        m.addConfiguredAttribute( archImplUrlAttr );
                    }
                }
            }
        }

        return m;
    }

    public JarArchiver getArchiver()
    {
        return archiver;
    }

    public void setArchiver( JarArchiver archiver )
    {
        this.archiver = archiver;
    }

    public void setOutputFile( File outputFile )
    {
        archiveFile = outputFile;
    }

    public void createArchive( MavenProject project, MavenArchiveConfiguration archiveConfiguration )
        throws ArchiverException, ManifestException, IOException, DependencyResolutionRequiredException
    {
        if ( archiveConfiguration.isAddMavenDescriptor() )
        {
            // ----------------------------------------------------------------------
            // We want to add the metadata for the project to the JAR in two forms:
            //
            // The first form is that of the POM itself. Applications that wish to
            // access the POM for an artifact using maven tools they can.
            //
            // The second form is that of a properties file containing the basic
            // top-level POM elements so that applications that wish to access
            // POM information without the use of maven tools can do so.
            // ----------------------------------------------------------------------
    
            // we have to clone the project instance so we can write out the pom with the deployment version,
            // without impacting the main project instance...
            MavenProject workingProject = new MavenProject( project );

            if ( workingProject.getArtifact().isSnapshot() )
            {
                workingProject.setVersion( workingProject.getArtifact().getVersion() );
            }

            String groupId = workingProject.getGroupId();

            String artifactId = workingProject.getArtifactId();

            File exportReadyPom = writeExportReadyPom( workingProject );

            archiver.addFile( exportReadyPom, "META-INF/maven/" + groupId + "/" + artifactId + "/pom.xml" );

            // ----------------------------------------------------------------------
            // Create pom.properties file
            // ----------------------------------------------------------------------

            Properties p = new Properties();

            p.setProperty( "groupId", workingProject.getGroupId() );

            p.setProperty( "artifactId", workingProject.getArtifactId() );

            p.setProperty( "version", workingProject.getVersion() );

            File pomPropertiesFile = new File( workingProject.getFile().getParentFile(), "pom.properties" );

            OutputStream os = new FileOutputStream( pomPropertiesFile );

            p.store( os, "Generated by Maven" );

            os.close(); // stream is flushed but not closed by Properties.store()

            archiver.addFile( pomPropertiesFile, "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties" );
        }

        // ----------------------------------------------------------------------
        // Create the manifest
        // ----------------------------------------------------------------------

        File manifestFile = archiveConfiguration.getManifestFile();

        if ( manifestFile != null )
        {
            archiver.setManifest( manifestFile );
        }

        Manifest manifest = getManifest( workingProject, archiveConfiguration.getManifest() );

        // any custom manifest entries in the archive configuration manifest?
        if ( !archiveConfiguration.isManifestEntriesEmpty() )
        {
            Map entries = archiveConfiguration.getManifestEntries();
            Set keys = entries.keySet();
            for ( Iterator iter = keys.iterator(); iter.hasNext(); )
            {
                String key = (String) iter.next();
                String value = (String) entries.get( key );
                Manifest.Attribute attr = new Manifest.Attribute( key, value );
                manifest.addConfiguredAttribute( attr );
            }
        }

        // any custom manifest sections in the archive configuration manifest?
        if ( !archiveConfiguration.isManifestSectionsEmpty() )
        {
        	List sections = archiveConfiguration.getManifestSections();
        	for ( Iterator iter = sections.iterator(); iter.hasNext(); )
        	{
        		ManifestSection section = (ManifestSection) iter.next();
        		Manifest.Section theSection = new Manifest.Section();
        		theSection.setName( section.getName() );
        		
        		if( !section.isManifestEntriesEmpty() ) {
        			Map entries = section.getManifestEntries();
        			Set keys = entries.keySet();
        			for ( Iterator it = keys.iterator(); it.hasNext(); )
        			{
                        String key = (String) it.next();
                        String value = (String) entries.get( key );
                        Manifest.Attribute attr = new Manifest.Attribute( key, value );
        				theSection.addConfiguredAttribute( attr );
        			}
        		}
        		
        		manifest.addConfiguredSection( theSection );
        	}
        }
        
        // Configure the jar
        archiver.addConfiguredManifest( manifest );

        archiver.setCompress( archiveConfiguration.isCompress() );

        archiver.setIndex( archiveConfiguration.isIndex() );

        archiver.setDestFile( archiveFile );

        // create archive
        archiver.createArchive();

        // Cleanup

        pomPropertiesFile.delete();
    }

    private File writeExportReadyPom( MavenProject project )
        throws IOException
    {
        String buildDirectory = project.getBuild().getDirectory();

        File buildDirectoryFile = new File( buildDirectory );

        buildDirectoryFile.mkdirs();

        File fullPom = new File( buildDirectoryFile, "exported-pom.xml" );

        FileWriter fWriter = null;

        try
        {
            fWriter = new FileWriter( fullPom );

            project.writeModel( fWriter );
        }
        finally
        {
            IOUtil.close( fWriter );
        }

        return fullPom;
    }
}
