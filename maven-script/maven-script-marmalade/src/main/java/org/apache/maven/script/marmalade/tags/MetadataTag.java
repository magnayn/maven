package org.apache.maven.script.marmalade.tags;

/*
 * Copyright 2001-2004 The Apache Software Foundation.
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

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.script.marmalade.MarmaladeMojoExecutionDirectives;
import org.codehaus.marmalade.model.AbstractMarmaladeTag;
import org.codehaus.marmalade.runtime.MarmaladeExecutionContext;
import org.codehaus.marmalade.runtime.MarmaladeExecutionException;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregator tag for the actual meat of the mojo.
 * 
 * @author jdcasey Created on Feb 8, 2005
 */
public class MetadataTag
    extends AbstractMarmaladeTag
    implements DescriptionParent
{

    private String id;

    private String goal;

    private boolean requiresDependencyResolution = true;

    private boolean requiresProject = true;

    private String instantiationStrategy;

    private String executionStrategy;

    private List parameters = new ArrayList();

    private String lifecyclePhase;

    private String description;

    protected boolean alwaysProcessChildren()
    {
        return false;
    }

    protected void doExecute( MarmaladeExecutionContext context ) throws MarmaladeExecutionException
    {
        processChildren( context );

        MojoDescriptor descriptor = buildDescriptor( context );
        context.setVariable( MarmaladeMojoExecutionDirectives.METADATA_OUTVAR, descriptor );
    }

    private MojoDescriptor buildDescriptor( MarmaladeExecutionContext context ) throws MarmaladeExecutionException
    {
        MojoDescriptor descriptor = new MojoDescriptor();

        descriptor.setLanguage( "marmalade" );

        if ( notEmpty( id ) )
        {
            descriptor.setId( id );
        }

        if ( notEmpty( goal ) )
        {
            descriptor.setGoal( goal );
        }

        if ( notEmpty( description ) )
        {
            descriptor.setDescription( description );
        }

        if ( notEmpty( executionStrategy ) )
        {
            descriptor.setExecutionStrategy( executionStrategy );
        }

        if ( notEmpty( instantiationStrategy ) )
        {
            descriptor.setInstantiationStrategy( instantiationStrategy );
        }

        descriptor.setParameters( parameters );
        descriptor.setRequiresDependencyResolution( requiresDependencyResolution );
        descriptor.setRequiresProject( requiresProject );

        String basePath = (String) context.getVariable( MarmaladeMojoExecutionDirectives.SCRIPT_BASEPATH_INVAR,
                                                        getExpressionEvaluator() );

        if(basePath.endsWith("/"))
        {
            basePath = basePath.substring(0, basePath.length()-2);
        }
        
        String implementationPath = getTagInfo().getSourceFile().substring( basePath.length() );

        descriptor.setImplementation( implementationPath );

        return descriptor;
    }

    private boolean notEmpty( String test )
    {
        return test != null && test.trim().length() > 0;
    }

    public void setId( String id )
    {
        this.id = id;
    }

    public void setLifecyclePhase( String lifecyclePhase )
    {
        this.lifecyclePhase = lifecyclePhase;
    }

    public void setGoal( String goal )
    {
        this.goal = goal;
    }

    public void setDescription( String description )
    {
        this.description = description;
    }

    public void setRequiresDependencyResolution( boolean requiresDependencyResolution )
    {
        this.requiresDependencyResolution = requiresDependencyResolution;
    }

    public void setRequiresProject( boolean requiresProject )
    {
        this.requiresProject = requiresProject;
    }

    public void setInstantiationStrategy( String instantiationStrategy )
    {
        this.instantiationStrategy = instantiationStrategy;
    }

    public void setExecutionStrategy( String executionStrategy )
    {
        this.executionStrategy = executionStrategy;
    }

    public void setParameters( List parameters )
    {
        this.parameters = parameters;
    }

}