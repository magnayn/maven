package org.apache.maven.plugin.jar;

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

import org.apache.maven.plugin.AbstractPlugin;
import org.apache.maven.plugin.PluginExecutionRequest;
import org.apache.maven.plugin.PluginExecutionResponse;
import org.apache.maven.project.MavenProject;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;

/**
 * @goal jar:install
 *
 * @description install a jar in local repository
 *
 * @prereq jar:jar
 *
 * @parameter
 *  name="jarName"
 *  type="String"
 *  required="true"
 *  validator=""
 *  expression="#maven.final.name"
 *  description=""
 *
 * @parameter
 *   name="outputDirectory"
 *   type="String"
 *   required="true"
 *   validator=""
 *   expression="#maven.build.dir"
 *   description=""
 *
 * @parameter
 *  name="project"
 *  type="org.apache.maven.project.MavenProject"
 *  required="true"
 *  validator=""
 *  expression="#project"
 *  description=""
 *
 * @parameter
 *  name="installer"
 *  type="org.apache.maven.artifact.installer.ArtifactInstaller"
 *  required="true"
 *  validator=""
 *  expression="#component.org.apache.maven.artifact.installer.ArtifactInstaller"
 *  description=""
 *
 *
 * @author <a href="michal.maczka@dimatics.com">Michal Maczka</a>
 * @version $Id$
 */
public class JarInstallMojo
        extends AbstractPlugin
{
    public void execute( PluginExecutionRequest request, PluginExecutionResponse response )
            throws Exception
    {

        String outputDirectory = ( String ) request.getParameter( "outputDirectory" );

        String jarName = ( String ) request.getParameter( "jarName" );

        File jarFile = new File( outputDirectory , jarName + ".jar" );

        MavenProject project = ( MavenProject ) request.getParameter( "project"  );

        ArtifactInstaller artifactInstaller = ( ArtifactInstaller ) request.getParameter( "component.org.apache.maven.artifact.installer.ArtifactInstaller"  );

        artifactInstaller.install( jarFile, "jar", project );

    }

}
