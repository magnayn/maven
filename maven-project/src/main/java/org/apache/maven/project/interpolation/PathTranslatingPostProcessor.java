package org.apache.maven.project.interpolation;

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

import org.apache.maven.project.path.PathTranslator;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;

import java.io.File;
import java.util.List;

/**
 * @version $Id: PathTranslatingPostProcessor.java 677447 2008-07-16 22:15:57Z jdcasey $
 */
public class PathTranslatingPostProcessor
    implements InterpolationPostProcessor
{

    private final List unprefixedPathKeys;

    private final File projectDir;

    private final PathTranslator pathTranslator;

    public PathTranslatingPostProcessor( List unprefixedPathKeys, File projectDir, PathTranslator pathTranslator )
    {
        this.unprefixedPathKeys = unprefixedPathKeys;
        this.projectDir = projectDir;
        this.pathTranslator = pathTranslator;
    }

    public Object execute( String expression, Object value )
    {
        if ( projectDir != null && value != null && unprefixedPathKeys.contains( expression ) )
        {
            return pathTranslator.alignToBaseDirectory( String.valueOf( value ), projectDir );
        }

        return value;
    }

}
