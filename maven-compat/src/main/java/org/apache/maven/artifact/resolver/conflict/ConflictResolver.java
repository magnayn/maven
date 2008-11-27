package org.apache.maven.artifact.resolver.conflict;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.artifact.resolver.ResolutionNode;

/**
 * Determines which version of an artifact to use when there are conflicting declarations.
 *
 * @author <a href="mailto:jason@maven.org">Jason van Zyl</a>
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 */
public interface ConflictResolver
{
    static String ROLE = ConflictResolver.class.getName();

    /**
     * Determines which of the specified versions of an artifact to use when there are conflicting declarations.
     *
     * @param node1 the first artifact declaration
     * @param node2 the second artifact declaration
     * @return the artifact declaration to use: <code>node1</code>; <code>node2</code>; or <code>null</code>if
     *         this conflict cannot be resolved
     * @since 3.0
     */
    ResolutionNode resolveConflict( ResolutionNode node1,
                                    ResolutionNode node2 );
}
