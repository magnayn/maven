package org.apache.maven.plugin;

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

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.util.Map;

/**
 * @author <a href="mailto:jason@maven.org">Jason van Zyl </a>
 * @version $Id$
 */
public interface PluginManager
{
    String ROLE = PluginManager.class.getName();

    void executeMojo( MavenProject project, MojoExecution execution, MavenSession session )
        throws MojoExecutionException, PluginManagerException, ArtifactResolutionException, MojoFailureException,
        ArtifactNotFoundException;

    MavenReport getReport( MavenProject project, MojoExecution mojoExecution, MavenSession session )
        throws PluginManagerException, ArtifactNotFoundException;

    PluginDescriptor getPluginDescriptorForPrefix( String prefix );

    Plugin getPluginDefinitionForPrefix( String prefix, MavenSession session, MavenProject project )
        throws PluginManagerException;

    PluginDescriptor verifyPlugin( Plugin plugin, MavenProject project, Settings settings,
                                   ArtifactRepository localRepository )
        throws ArtifactResolutionException, PluginManagerException, PluginVersionResolutionException,
        ArtifactNotFoundException;

    PluginDescriptor verifyReportPlugin( ReportPlugin reportPlugin, MavenProject project, MavenSession session )
        throws PluginVersionResolutionException, ArtifactResolutionException, PluginManagerException,
        ArtifactNotFoundException;

    Object getPluginComponent( Plugin plugin, String role, String roleHint )
        throws ComponentLookupException, PluginManagerException;

    Map getPluginComponents( Plugin plugin, String role )
        throws ComponentLookupException, PluginManagerException;
}