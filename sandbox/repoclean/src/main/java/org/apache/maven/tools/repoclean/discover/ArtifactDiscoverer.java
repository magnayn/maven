package org.apache.maven.tools.repoclean.discover;

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

import org.apache.maven.tools.repoclean.report.PathLister;
import org.apache.maven.tools.repoclean.report.ReportWriteException;
import org.apache.maven.tools.repoclean.report.Reporter;

import java.io.File;
import java.util.List;

/**
 * @author jdcasey
 */
public interface ArtifactDiscoverer
{
    String ROLE = ArtifactDiscoverer.class.getName();

    String[] STANDARD_DISCOVERY_EXCLUDES = {"bin/**", "reports/**", ".maven/**", "**/poms/*.pom", "**/*.md5",
        "**/*.MD5", "**/*.sha1", "**/*.SHA1", "**/*snapshot-version", "*/website/**", "*/licenses/**", "*/licences/**",
        "**/.htaccess", "**/*.html", "**/*.asc", "**/*.txt", "**/*.xml", "**/README*", "**/CHANGELOG*", "**/KEYS*"};

    List discoverArtifacts( File repositoryBase, Reporter reporter, String blacklistedPatterns,
                            PathLister excludeLister, PathLister kickoutLister, boolean includeSnapshots )
        throws ReportWriteException;

}