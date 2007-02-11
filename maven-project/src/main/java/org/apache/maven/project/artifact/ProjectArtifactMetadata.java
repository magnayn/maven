package org.apache.maven.project.artifact;

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
import org.apache.maven.artifact.metadata.AbstractArtifactMetadata;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.metadata.RepositoryMetadataStoreException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * Attach a POM to an artifact.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public class ProjectArtifactMetadata
    extends AbstractArtifactMetadata
{
    private final File file;

    public ProjectArtifactMetadata( Artifact artifact )
    {
        this( artifact, null );
    }

    public ProjectArtifactMetadata( Artifact artifact,
                                    File file )
    {
        super( artifact );
        this.file = file;
    }

    public String getRemoteFilename()
    {
        return getFilename();
    }

    public String getLocalFilename( ArtifactRepository repository )
    {
        return getFilename();
    }

    private String getFilename()
    {
        return getArtifactId() + "-" + artifact.getVersion() + ".pom";
    }

    public void storeInLocalRepository( ArtifactRepository localRepository,
                                        ArtifactRepository remoteRepository )
        throws RepositoryMetadataStoreException
    {
        File destination = new File( localRepository.getBasedir(),
                                     localRepository.pathOfLocalRepositoryMetadata( this, remoteRepository ) );

        // ----------------------------------------------------------------------------
        // I'm fully aware that the file could just be moved using File.rename but
        // there are bugs in various JVM that have problems doing this across
        // different filesystem. So we'll incur the small hit to actually copy
        // here and be safe. jvz.
        // ----------------------------------------------------------------------------

        Reader reader = null;

        Writer writer = null;

        try
        {
            reader = new FileReader( file );

            writer = new FileWriter( destination );

            IOUtil.copy( reader, writer );
        }
        catch ( FileNotFoundException e )
        {
            throw new RepositoryMetadataStoreException( "Error rewriting POM", e );
        }
        catch ( IOException e )
        {
            throw new RepositoryMetadataStoreException( "Error rewriting POM", e );
        }
        finally
        {
            IOUtil.close( reader );

            IOUtil.close( writer );
        }
    }

    public String toString()
    {
        return "project information for " + artifact.getArtifactId() + " " + artifact.getVersion();
    }

    public boolean storedInArtifactVersionDirectory()
    {
        return true;
    }

    public String getBaseVersion()
    {
        return artifact.getBaseVersion();
    }

    public Object getKey()
    {
        return "project " + artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    public void merge( ArtifactMetadata metadata )
    {
        ProjectArtifactMetadata m = (ProjectArtifactMetadata) metadata;
        if ( !m.file.equals( file ) )
        {
            throw new IllegalStateException( "Cannot add two different pieces of metadata for: " + getKey() );
        }
    }
}
