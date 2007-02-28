package org.apache.maven.plugin;

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

import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.version.PluginVersionNotFoundException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.reporting.MavenReport;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.Map;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl </a>
 * @version $Id$
 */
public interface PluginManager
{
    String ROLE = PluginManager.class.getName();

    void executeMojo( MavenProject project,
                      MojoExecution execution,
                      MavenSession session )
        throws MojoExecutionException, ArtifactResolutionException, MojoFailureException, ArtifactNotFoundException,
        InvalidDependencyVersionException, PluginManagerException, PluginConfigurationException;

    MavenReport getReport( MavenProject project,
                           MojoExecution mojoExecution,
                           MavenSession session )
        throws ArtifactNotFoundException, PluginConfigurationException, PluginManagerException,
        ArtifactResolutionException;

    PluginDescriptor getPluginDescriptorForPrefix( String prefix );

    Plugin getPluginDefinitionForPrefix( String prefix,
                                         MavenSession session,
                                         MavenProject project );

    PluginDescriptor verifyPlugin( Plugin plugin,
                                   MavenProject project,
                                   MavenSession session )
        throws ArtifactResolutionException, PluginVersionResolutionException, ArtifactNotFoundException,
        InvalidVersionSpecificationException, InvalidPluginException, PluginManagerException, PluginNotFoundException,
        PluginVersionNotFoundException;

    PluginDescriptor verifyReportPlugin( ReportPlugin reportPlugin,
                                         MavenProject project,
                                         MavenSession session )
        throws PluginVersionResolutionException, ArtifactResolutionException, ArtifactNotFoundException,
        InvalidVersionSpecificationException, InvalidPluginException, PluginManagerException, PluginNotFoundException,
        PluginVersionNotFoundException;

    Object getPluginComponent( Plugin plugin,
                               String role,
                               String roleHint )
        throws PluginManagerException, ComponentLookupException;

    Map getPluginComponents( Plugin plugin,
                             String role )
        throws ComponentLookupException, PluginManagerException;

}