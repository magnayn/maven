package org.apache.maven.repository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.DefaultArtifactRepository;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role=MirrorBuilder.class)
public class DefaultMirrorBuilder
    implements MirrorBuilder    
{    
    private static final String WILDCARD = "*";

    private static final String EXTERNAL_WILDCARD = "external:*";

    private static int anonymousMirrorIdSeed = 0;
    
    @Requirement
    private Logger logger;
    
    @Requirement 
    private ArtifactRepositoryFactory repositoryFactory;
    
    //used LinkedMap to preserve the order.
    private Map<String, ArtifactRepository> mirrors = new LinkedHashMap<String, ArtifactRepository>();

    public void addMirror( String id, String mirrorOf, String url )
    {        
        if ( id == null )
        {
            id = "mirror-" + anonymousMirrorIdSeed++;
            logger.warn( "You are using a mirror that doesn't declare an <id/> element. Using \'" + id + "\' instead:\nId: " + id + "\nmirrorOf: " + mirrorOf + "\nurl: " + url + "\n" );
        }

        ArtifactRepository mirror = new DefaultArtifactRepository( id, url, null );

        System.out.println( mirror + " --> " + mirrorOf );

        mirrors.put( mirrorOf, mirror );
    }
        
    /**
     * This method finds a matching mirror for the selected repository. If there is an exact match,
     * this will be used. If there is no exact match, then the list of mirrors is examined to see if
     * a pattern applies.
     * 
     * @param originalRepository See if there is a mirror for this repository.
     * @return the selected mirror or null if none are found.
     */
    public ArtifactRepository getMirror( ArtifactRepository originalRepository )
    {
        ArtifactRepository selectedMirror = mirrors.get( originalRepository.getId() );
        if ( null == selectedMirror )
        {
            // Process the patterns in order. First one that matches wins.
            Set<String> keySet = mirrors.keySet();
            if ( keySet != null )
            {
                for ( String pattern : keySet )
                {
                    if ( matchPattern( originalRepository, pattern ) )
                    {
                        selectedMirror = mirrors.get( pattern );
                    }
                }
            }

        }
        
        return selectedMirror;
    }

    public void clearMirrors()
    {
        mirrors.clear();    
        anonymousMirrorIdSeed = 0;
    }       
    
    // Make these available to tests
    
    ArtifactRepository getMirrorRepository( ArtifactRepository repository )
    {
        ArtifactRepository mirror = getMirror( repository );
        if ( mirror != null )
        {
            String id = mirror.getId();
            if ( id == null )
            {
                // TODO: this should be illegal in settings.xml
                id = repository.getId();
            }

            logger.debug( "Using mirror: " + mirror.getId() + " for repository: " + repository.getId() + "\n(mirror url: " + mirror.getUrl() + ")" );
            repository = repositoryFactory.createArtifactRepository( id, mirror.getUrl(),
                                                                     repository.getLayout(), repository.getSnapshots(),
                                                                     repository.getReleases() );
        }
        return repository;
    }    
        
    /**
     * This method checks if the pattern matches the originalRepository. Valid patterns: * =
     * everything external:* = everything not on the localhost and not file based. repo,repo1 = repo
     * or repo1 *,!repo1 = everything except repo1
     * 
     * @param originalRepository to compare for a match.
     * @param pattern used for match. Currently only '*' is supported.
     * @return true if the repository is a match to this pattern.
     */
    boolean matchPattern( ArtifactRepository originalRepository, String pattern )
    {
        boolean result = false;
        String originalId = originalRepository.getId();

        // simple checks first to short circuit processing below.
        if ( WILDCARD.equals( pattern ) || pattern.equals( originalId ) )
        {
            result = true;
        }
        else
        {
            // process the list
            String[] repos = pattern.split( "," );
            for ( String repo : repos )
            {
                // see if this is a negative match
                if ( repo.length() > 1 && repo.startsWith( "!" ) )
                {
                    if ( originalId.equals( repo.substring( 1 ) ) )
                    {
                        // explicitly exclude. Set result and stop processing.
                        result = false;
                        break;
                    }
                }
                // check for exact match
                else if ( originalId.equals( repo ) )
                {
                    result = true;
                    break;
                }
                // check for external:*
                else if ( EXTERNAL_WILDCARD.equals( repo ) && isExternalRepo( originalRepository ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
                else if ( WILDCARD.equals( repo ) )
                {
                    result = true;
                    // don't stop processing in case a future segment explicitly excludes this repo
                }
            }
        }
        return result;
    }
    
    
    /**
     * Checks the URL to see if this repository refers to an external repository
     * 
     * @param originalRepository
     * @return true if external.
     */
    boolean isExternalRepo( ArtifactRepository originalRepository )
    {
        try
        {
            URL url = new URL( originalRepository.getUrl() );
            return !( url.getHost().equals( "localhost" ) || url.getHost().equals( "127.0.0.1" ) || url.getProtocol().equals( "file" ) );
        }
        catch ( MalformedURLException e )
        {
            // bad url just skip it here. It should have been validated already, but the wagon lookup will deal with it
            return false;
        }
    }
}
