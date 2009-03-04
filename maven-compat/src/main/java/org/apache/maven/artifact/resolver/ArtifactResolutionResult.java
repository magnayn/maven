package org.apache.maven.artifact.resolver;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Specific problems during resolution that we want to account for:
 * <p/>
 * - missing metadata - version range violations - version circular dependencies - missing artifacts
 * - network/transfer errors - file system errors: permissions
 * 
 * @author Jason van Zyl
 * @TODO carlos: all these possible has*Exceptions and get*Exceptions methods make the clients too
 *       complex requiring a long list of checks, need to create a parent/interfact/encapsulation
 *       for the types of exceptions
 */
public class ArtifactResolutionResult
{
    private Artifact originatingArtifact;

    private List<Artifact> missingArtifacts;

    // Exceptions

    private List<Exception> exceptions = new ArrayList<Exception>();

    private List<Exception> versionRangeViolations;

    private List<ArtifactResolutionException> metadataResolutionExceptions;

    private List<CyclicDependencyException> circularDependencyExceptions;

    private List<ArtifactResolutionException> errorArtifactExceptions;

    // file system errors

    private List<ArtifactRepository> repositories;

    private Set<Artifact> requestedArtifacts;

    private Set<Artifact> artifacts;

    public Artifact getOriginatingArtifact()
    {
        return originatingArtifact;
    }

    public ArtifactResolutionResult ListOriginatingArtifact( final Artifact originatingArtifact )
    {
        this.originatingArtifact = originatingArtifact;

        return this;
    }

    public void addArtifact( Artifact artifact )
    {
        if ( artifacts == null )
        {
            artifacts = new LinkedHashSet<Artifact>();
        }

        artifacts.add( artifact );
    }

    public Set<Artifact> getArtifacts()
    {
        return artifacts;
    }

    public void addRequestedArtifact( Artifact artifact )
    {
        if ( requestedArtifacts == null )
        {
            requestedArtifacts = new LinkedHashSet<Artifact>();
        }

        requestedArtifacts.add( artifact );
    }

    public Set<Artifact> getRequestedArtifacts()
    {
        return requestedArtifacts;
    }

    public List<Artifact> getMissingArtifacts()
    {
        return missingArtifacts == null ? Collections.<Artifact> emptyList() : missingArtifacts;
    }

    public ArtifactResolutionResult addMissingArtifact( Artifact artifact )
    {
        missingArtifacts = initList( missingArtifacts );

        missingArtifacts.add( artifact );

        return this;
    }

    public ArtifactResolutionResult setUnresolvedArtifacts( final List<Artifact> unresolvedArtifacts )
    {
        this.missingArtifacts = unresolvedArtifacts;

        return this;
    }

    // ------------------------------------------------------------------------
    // Exceptions
    // ------------------------------------------------------------------------

    public boolean hasExceptions()
    {
        return exceptions.size() > 0;
    }

    public List<Exception> getExceptions()
    {
        return exceptions;
    }

    // ------------------------------------------------------------------------
    // Version Range Violations
    // ------------------------------------------------------------------------

    public boolean hasVersionRangeViolations()
    {
        return versionRangeViolations != null;
    }

    /**
     * @TODO this needs to accept a {@link OverConstrainedVersionException} as returned by
     *       {@link #getVersionRangeViolation(int)} but it's not used like that in
     *       {@link DefaultArtifactCollector}
     */
    public ArtifactResolutionResult addVersionRangeViolation( Exception e )
    {
        versionRangeViolations = initList( versionRangeViolations );

        versionRangeViolations.add( e );

        exceptions.add( e );

        return this;
    }

    public OverConstrainedVersionException getVersionRangeViolation( int i )
    {
        return (OverConstrainedVersionException) versionRangeViolations.get( i );
    }

    public List getVersionRangeViolations()
    {
        return versionRangeViolations == null ? Collections.EMPTY_LIST : versionRangeViolations;
    }

    // ------------------------------------------------------------------------
    // Metadata Resolution Exceptions: ArtifactResolutionExceptions
    // ------------------------------------------------------------------------

    public boolean hasMetadataResolutionExceptions()
    {
        return metadataResolutionExceptions != null;
    }

    public ArtifactResolutionResult addMetadataResolutionException( ArtifactResolutionException e )
    {
        metadataResolutionExceptions = initList( metadataResolutionExceptions );

        metadataResolutionExceptions.add( e );

        exceptions.add( e );

        return this;
    }

    public ArtifactResolutionException getMetadataResolutionException( int i )
    {
        return metadataResolutionExceptions.get( i );
    }

    public List getMetadataResolutionExceptions()
    {
        return metadataResolutionExceptions == null ? Collections.EMPTY_LIST : metadataResolutionExceptions;
    }

    // ------------------------------------------------------------------------
    // ErrorArtifactExceptions: ArtifactResolutionExceptions
    // ------------------------------------------------------------------------

    public boolean hasErrorArtifactExceptions()
    {
        return errorArtifactExceptions != null;
    }

    public ArtifactResolutionResult addErrorArtifactException( ArtifactResolutionException e )
    {
        errorArtifactExceptions = initList( errorArtifactExceptions );

        errorArtifactExceptions.add( e );

        exceptions.add( e );

        return this;
    }

    public List<ArtifactResolutionException> getErrorArtifactExceptions()
    {
        if ( errorArtifactExceptions == null )
        {
            return Collections.emptyList();
        }

        return errorArtifactExceptions;
    }

    // ------------------------------------------------------------------------
    // Circular Dependency Exceptions
    // ------------------------------------------------------------------------

    public boolean hasCircularDependencyExceptions()
    {
        return circularDependencyExceptions != null;
    }

    public ArtifactResolutionResult addCircularDependencyException( CyclicDependencyException e )
    {
        circularDependencyExceptions = initList( circularDependencyExceptions );

        circularDependencyExceptions.add( e );

        exceptions.add( e );

        return this;
    }

    public CyclicDependencyException getCircularDependencyException( int i )
    {
        return circularDependencyExceptions.get( i );
    }

    public List<CyclicDependencyException> getCircularDependencyExceptions()
    {
        if ( circularDependencyExceptions == null )
        {
            return Collections.emptyList();
        }

        return circularDependencyExceptions;
    }

    // ------------------------------------------------------------------------
    // Repositories
    // ------------------------------------------------------------------------

    public List<ArtifactRepository> getRepositories()
    {
        if ( repositories == null )
        {
            return Collections.emptyList();
        }

        return repositories;
    }

    public ArtifactResolutionResult setRepositories( final List<ArtifactRepository> repositories )
    {
        this.repositories = repositories;

        return this;
    }

    //
    // Internal
    //

    private <T> List<T> initList( final List<T> l )
    {
        if ( l == null )
        {
            return new ArrayList<T>();
        }
        return l;
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        if ( artifacts != null )
        {
            int i = 1;
            sb.append( "---------" ).append( "\n" );
            sb.append( artifacts.size() ).append( "\n" );
            for ( Artifact a : artifacts )
            {
                if ( a.getClassifier() == null )
                {
                    sb.append( "NULL NULL" );
                    continue;
                }

                sb.append( i ).append( " " ).append( a ).append( "\n" );
                i++;
            }
            sb.append( "---------" ).append( "\n" );
        }
        else
        {
            System.out.println( "NULL set of artifacts!");
        }
        

        return sb.toString();
    }
}
