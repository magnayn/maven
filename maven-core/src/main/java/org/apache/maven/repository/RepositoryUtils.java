package org.apache.maven.repository;

/* ====================================================================
 *   Copyright 2001-2004 The Apache Software Foundation.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * ====================================================================
 */

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Repository;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * @todo not sure "wagon" notation is appropriate here - it is really maven-artifact which is not the same as wagon
 * @author <a href="mailto:michal@codehaus.org">Michal Maczka</a>
 * @version $Id$
 */
public class RepositoryUtils
{
    public static Set mavenToWagon( List repositories )
    {
        Set repos = new HashSet();

        for ( Iterator i = repositories.iterator(); i.hasNext(); )
        {
            repos.add( mavenRepositoryToWagonRepository( (Repository) i.next() ) );
        }

        return repos;
    }
    public static ArtifactRepository
        mavenRepositoryToWagonRepository( Repository mavenRepository )
    {
        ArtifactRepository retValue = new ArtifactRepository();

        retValue.setUrl( mavenRepository.getUrl() );

        return retValue;
    }

    public static ArtifactRepository localRepositoryToWagonRepository( String repository )
    {
        return new ArtifactRepository( "local", "file://" + repository );
    }
}
