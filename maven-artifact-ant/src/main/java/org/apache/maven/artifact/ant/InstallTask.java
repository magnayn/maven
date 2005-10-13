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
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.apache.tools.ant.BuildException;

import java.io.File;

/**
 * Install task, using maven-artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 * @todo should be able to incorporate into the install mojo?
 */
public class InstallTask
    extends AbstractArtifactTask
{
    private File file;

    protected void doExecute()
    {
        ArtifactRepository localRepo = createLocalArtifactRepository();

        MavenProjectBuilder builder = (MavenProjectBuilder) lookup( MavenProjectBuilder.ROLE );
        Pom pom = buildPom( builder, localRepo );

        Artifact artifact = createArtifact( pom );

        boolean isPomArtifact = "pom".equals( pom.getPackaging() );
        if ( !isPomArtifact )
        {
            ArtifactMetadata metadata = new ProjectArtifactMetadata( artifact, pom.getFile() );
            artifact.addMetadata( metadata );
        }

        ArtifactInstaller installer = (ArtifactInstaller) lookup( ArtifactInstaller.ROLE );
        try
        {
            if ( !isPomArtifact )
            {
                installer.install( file, artifact, localRepo );
            }
            else
            {
                installer.install( pom.getFile(), artifact, localRepo );
            }
        }
        catch ( ArtifactInstallationException e )
        {
            // TODO: install exception that does not give a trace
            throw new BuildException( "Error installing artifact", e );
        }
    }

    public File getFile()
    {
        return file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }
}
