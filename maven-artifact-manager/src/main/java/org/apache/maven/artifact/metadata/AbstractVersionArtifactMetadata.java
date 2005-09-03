package org.apache.maven.artifact.metadata;

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
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Base version artifact metadata.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 * @version $Id$
 */
public abstract class AbstractVersionArtifactMetadata
    extends AbstractArtifactMetadata
    implements VersionArtifactMetadata
{
    protected static final String SNAPSHOT_VERSION_FILE = "version.txt";

    protected long lastModified;

    public AbstractVersionArtifactMetadata( Artifact artifact )
    {
        super( artifact );
    }

    protected File getLocalRepositoryLocation( ArtifactRepository localRepository, ArtifactRepository remoteRepository )
    {
        return new File( localRepository.getBasedir(),
                         localRepository.pathOfLocalRepositoryMetadata( this, remoteRepository ) );
    }

    private void readFromFile( File file )
        throws IOException
    {
        setContent( FileUtils.fileRead( file ) );
        lastModified = file.lastModified();
    }

    protected abstract void setContent( String content );

    public boolean exists()
    {
        return lastModified > 0;
    }

    public Date getLastModified()
    {
        return new Date( lastModified );
    }

    public void readFromLocalRepository( ArtifactRepository localRepository, ArtifactRepository remoteRepository )
        throws IOException
    {
        File f = getLocalRepositoryLocation( localRepository, remoteRepository );
        if ( f.exists() )
        {
            readFromFile( f );
        }
    }

    public void retrieveFromRemoteRepository( ArtifactRepository remoteRepository, WagonManager wagonManager,
                                              String checksumPolicy )
        throws ArtifactMetadataRetrievalException, ResourceDoesNotExistException
    {
        try
        {
            // TODO: shouldn't need a file intermediatary - improve wagon to take a stream
            File destination = File.createTempFile( "maven-artifact", null );
            destination.deleteOnExit();

            wagonManager.getArtifactMetadata( this, remoteRepository, destination, checksumPolicy );

            readFromFile( destination );
        }
        catch ( TransferFailedException e )
        {
            throw new ArtifactMetadataRetrievalException( "Unable to retrieve metadata", e );
        }
        catch ( IOException e )
        {
            throw new ArtifactMetadataRetrievalException( "Unable to retrieve metadata", e );
        }
    }

    public void storeInLocalRepository( ArtifactRepository localRepository, ArtifactRepository remoteRepository )
        throws ArtifactMetadataRetrievalException
    {
        String version = constructVersion();
        if ( version != null )
        {
            try
            {
                String path = getLocalRepositoryLocation( localRepository, remoteRepository ).getPath();
                File file = new File( path );
                // TODO: this should be centralised before the resolution of the artifact
                file.getParentFile().mkdirs();
                FileUtils.fileWrite( path, version );
                lastModified = file.lastModified();
            }
            catch ( IOException e )
            {
                throw new ArtifactMetadataRetrievalException( "Unable to retrieve metadata", e );
            }
        }
    }
}
