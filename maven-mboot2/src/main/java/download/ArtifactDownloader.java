package download;

import model.Dependency;
import model.Repository;
import util.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ArtifactDownloader
{
    public static final String SNAPSHOT_SIGNATURE = "-SNAPSHOT";

    private boolean useTimestamp = true;

    private boolean ignoreErrors = false;

    private String proxyHost;

    private String proxyPort;

    private String proxyUserName;

    private String proxyPassword;

    private Repository localRepository;

    private static final String REPO_URL = "http://repo1.maven.org/maven2";

    private Map downloadedArtifacts = new HashMap();

    private List remoteRepositories;

    public ArtifactDownloader( Repository localRepository )
        throws Exception
    {
        if ( localRepository == null )
        {
            System.err.println( "local repository not specified" );

            System.exit( 1 );
        }

        this.localRepository = localRepository;
    }

    public void setProxy( String host, String port, String userName, String password )
    {
        proxyHost = host;
        proxyPort = port;
        proxyUserName = userName;
        proxyPassword = password;
        System.out.println( "Using the following proxy : " + proxyHost + "/" + proxyPort );
    }

    public void downloadDependencies( Collection dependencies )
        throws DownloadFailedException
    {
        for ( Iterator j = dependencies.iterator(); j.hasNext(); )
        {
            Dependency dep = (Dependency) j.next();

            String dependencyConflictId = dep.getDependencyConflictId();
            if ( !downloadedArtifacts.containsKey( dependencyConflictId ) )
            {
                File destinationFile = localRepository.getArtifactFile( dep );
                // The directory structure for this project may
                // not exists so create it if missing.
                File directory = destinationFile.getParentFile();

                if ( directory.exists() == false )
                {
                    directory.mkdirs();
                }

                boolean snapshot = isSnapshot( dep );

                if ( dep.getGroupId().equals( "org.apache.maven" ) && snapshot )
                {
                    //skip our own
                    continue;
                }

                if ( !getRemoteArtifact( dep, destinationFile ) )
                {
                    throw new DownloadFailedException( "Failed to download " + dep );
                }

                downloadedArtifacts.put( dependencyConflictId, dep );
            }
            else
            {
                Dependency d = (Dependency) downloadedArtifacts.get( dependencyConflictId );
                dep.setResolvedVersion( d.getResolvedVersion() );
            }
        }
    }

    private static boolean isSnapshot( Dependency dep )
    {
        return dep.getVersion().indexOf( SNAPSHOT_SIGNATURE ) >= 0;
    }

    private boolean getRemoteArtifact( Dependency dep, File destinationFile )
    {
        boolean fileFound = false;

        for ( Iterator i = getRemoteRepositories().iterator(); i.hasNext(); )
        {
            Repository remoteRepo = (Repository) i.next();

            boolean snapshot = isSnapshot( dep );
            if ( snapshot && !remoteRepo.isSnapshots() )
            {
                continue;
            }
            if ( !snapshot && !remoteRepo.isReleases() )
            {
                continue;
            }

            // The username and password parameters are not being used here.
            String url = remoteRepo.getBasedir() + "/" + remoteRepo.getArtifactPath( dep );

            // Attempt to retrieve the artifact and set the checksum if retrieval
            // of the checksum file was successful.
            try
            {
                String version = dep.getVersion();
                if ( snapshot )
                {
                    String filename = "maven-metadata-" + remoteRepo.getId() + ".xml";
                    File localFile = localRepository.getMetadataFile( dep.getGroupId(), dep.getArtifactId(),
                                                                      dep.getVersion(), dep.getType(),
                                                                      "maven-metadata-local.xml" );
                    File remoteFile = localRepository.getMetadataFile( dep.getGroupId(), dep.getArtifactId(),
                                                                       dep.getVersion(), dep.getType(), filename );
                    String metadataPath = remoteRepo.getMetadataPath( dep.getGroupId(), dep.getArtifactId(),
                                                                      dep.getVersion(), dep.getType(), "maven-metadata.xml" );
                    String metaUrl = remoteRepo.getBasedir() + "/" + metadataPath;
                    log( "Downloading " + metaUrl );
                    try
                    {
                        HttpUtils.getFile( metaUrl, remoteFile, ignoreErrors, true, proxyHost, proxyPort, proxyUserName,
                                           proxyPassword, false );
                    }
                    catch ( IOException e )
                    {
                        log( "WARNING: remote metadata version not found, using local: " + e.getMessage() );
                    }

                    File file = localFile;
                    if ( remoteFile.exists() )
                    {
                        if ( !localFile.exists() || localFile.lastModified() < remoteFile.lastModified() )
                        {
                            file = remoteFile;
                        }
                    }

                    boolean found = false;
                    if ( file.exists() )
                    {
                        log( "Using metadata: " + file );

                        RepositoryMetadata metadata = RepositoryMetadata.read( file );

                        if ( version.equals( metadata.getVersion() ) )
                        {
                            found = true;
                            version = metadata.constructVersion( version );
                            log( "Resolved version: " + version );
                            dep.setResolvedVersion( version );
                            if ( !version.endsWith( "SNAPSHOT" ) )
                            {
                                String ver = version.substring(
                                    version.lastIndexOf( "-", version.lastIndexOf( "-" ) - 1 ) + 1 );
                                String extension = url.substring( url.length() - 4 );
                                url = getSnapshotMetadataFile( url, ver + extension );
                            }
                            else if ( destinationFile.exists() )
                            {
                                // It's already there
                                return true;
                            }
                        }
                        else
                        {
                            log( "WARNING: versions did not match, not using metadata (" + version + " vs " +
                                metadata.getVersion() + ")" );
                        }
                    }

                    if ( !found )
                    {
                        log( "WARNING: attempting to use legacy metadata" );

                        filename = getSnapshotMetadataFile( destinationFile.getName(), "SNAPSHOT.version.txt" );
                        file = localRepository.getMetadataFile( dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                                                                dep.getType(), filename );
                        metadataPath = remoteRepo.getMetadataPath( dep.getGroupId(), dep.getArtifactId(),
                                                                   dep.getVersion(), dep.getType(), filename );
                        metaUrl = remoteRepo.getBasedir() + "/" + metadataPath;
                        log( "Downloading " + metaUrl );
                        try
                        {
                            HttpUtils.getFile( metaUrl, file, ignoreErrors, true, proxyHost, proxyPort, proxyUserName,
                                               proxyPassword, false );
                        }
                        catch ( IOException e )
                        {
                            log( "WARNING: remote SNAPSHOT version not found, using local: " + e.getMessage() );
                        }

                        if ( file.exists() )
                        {
                            version = FileUtils.fileRead( file );
                            log( "Resolved version: " + version );
                            dep.setResolvedVersion( version );
                            if ( !version.endsWith( "SNAPSHOT" ) )
                            {
                                String ver = version.substring(
                                    version.lastIndexOf( "-", version.lastIndexOf( "-" ) - 1 ) + 1 );
                                String extension = url.substring( url.length() - 4 );
                                url = getSnapshotMetadataFile( url, ver + extension );
                            }
                            else if ( destinationFile.exists() )
                            {
                                // It's already there
                                return true;
                            }
                        }
                        else
                        {
                            log( "WARNING: local SNAPSHOT version not found, using default" );
                        }
                    }
                }
                if ( !"pom".equals( dep.getType() ) )
                {
                    String name = dep.getArtifactId() + "-" + dep.getResolvedVersion() + ".pom";
                    File file = localRepository.getMetadataFile( dep.getGroupId(), dep.getArtifactId(),
                                                                 dep.getVersion(), dep.getType(), name );

                    file.getParentFile().mkdirs();

                    if ( !file.exists() || version.indexOf( "SNAPSHOT" ) >= 0 )
                    {
                        String filename = dep.getArtifactId() + "-" + version + ".pom";
                        String metadataPath = remoteRepo.getMetadataPath( dep.getGroupId(), dep.getArtifactId(),
                                                                          dep.getVersion(), dep.getType(), filename );
                        String metaUrl = remoteRepo.getBasedir() + "/" + metadataPath;
                        log( "Downloading " + metaUrl );

                        try
                        {
                            HttpUtils.getFile( metaUrl, file, ignoreErrors, false, proxyHost, proxyPort, proxyUserName,
                                               proxyPassword, false );
                        }
                        catch ( IOException e )
                        {
                            log( "Couldn't find POM - ignoring: " + e.getMessage() );
                        }
                    }
                }

                destinationFile = localRepository.getArtifactFile( dep );
                if ( !destinationFile.exists() )
                {
                    log( "Downloading " + url );
                    HttpUtils.getFile( url, destinationFile, ignoreErrors, useTimestamp, proxyHost, proxyPort,
                                       proxyUserName, proxyPassword, true );
                }

                // Artifact was found, continue checking additional remote repos (if any)
                // in case there is a newer version (i.e. snapshots) in another repo
                fileFound = true;
            }
            catch ( FileNotFoundException e )
            {
                log( "Artifact not found at [" + url + "]" );
                // Ignore
            }
            catch ( Exception e )
            {
                // If there are additional remote repos, then ignore exception
                // as artifact may be found in another remote repo. If there
                // are no more remote repos to check and the artifact wasn't found in
                // a previous remote repo, then artifactFound is false indicating
                // that the artifact could not be found in any of the remote repos
                //
                // arguably, we need to give the user better control (another command-
                // line switch perhaps) of what to do in this case? Maven already has
                // a command-line switch to work in offline mode, but what about when
                // one of two or more remote repos is unavailable? There may be multiple
                // remote repos for redundancy, in which case you probably want the build
                // to continue. There may however be multiple remote repos because some
                // artifacts are on one, and some are on another. In this case, you may
                // want the build to break.
                //
                // print a warning, in any case, so user catches on to mistyped
                // hostnames, or other snafus
                log( "Error retrieving artifact from [" + url + "]: " + e );
            }
        }

        return fileFound;
    }

    private static String getSnapshotMetadataFile( String filename, String s )
    {
        int index = filename.lastIndexOf( "SNAPSHOT" );
        return filename.substring( 0, index ) + s;
    }

    private void log( String message )
    {
        System.out.println( message );
    }

    public Repository getLocalRepository()
    {
        return localRepository;
    }

    public List getRemoteRepositories()
    {
        if ( remoteRepositories == null )
        {
            remoteRepositories = new ArrayList();
        }

        if ( remoteRepositories.isEmpty() )
        {
            // TODO: use super POM?
            remoteRepositories.add( new Repository( "central", REPO_URL, Repository.LAYOUT_DEFAULT, false, true ) );
            // TODO: use maven root POM?
            remoteRepositories.add( new Repository( "snapshots", "http://snapshots.maven.codehaus.org/maven2/",
                                                    Repository.LAYOUT_DEFAULT, true, false ) );
        }

        return remoteRepositories;
    }

    public void setRemoteRepositories( List remoteRepositories )
    {
        this.remoteRepositories = remoteRepositories;
    }
}
