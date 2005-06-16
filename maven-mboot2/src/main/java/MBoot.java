
import compile.CompilerConfiguration;
import compile.JavacCompiler;
import download.ArtifactDownloader;
import jar.JarMojo;
import model.Dependency;
import model.ModelReader;
import model.Repository;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import test.SurefirePlugin;
import util.AbstractReader;
import util.Commandline;
import util.FileUtils;
import util.IOUtil;
import util.IsolatedClassLoader;
import util.Os;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MBoot
{
    String[] builds = new String[]{"maven-model", "maven-monitor", "maven-plugin-api",
                                   "maven-plugin-descriptor", "maven-artifact", "maven-script/maven-script-marmalade",
                                   "maven-script/maven-script-beanshell", "maven-project",
                                   "maven-settings", "maven-profile", "maven-plugin-registry",
                                   "maven-reporting/maven-reporting-api", "maven-core", "maven-archiver",
                                   "maven-plugin-tools/maven-plugin-tools-api",
                                   "maven-plugin-tools/maven-plugin-tools-java",
                                   "maven-plugin-tools/maven-plugin-tools-beanshell",
                                   "maven-plugin-tools/maven-plugin-tools-pluggy",
                                   "maven-plugin-tools/maven-plugin-tools-marmalade", "maven-core-it-verifier"};

    String[] pluginBuilds = new String[]{"maven-plugins/maven-clean-plugin", "maven-plugins/maven-compiler-plugin",
                                         "maven-plugins/maven-install-plugin", "maven-plugins/maven-jar-plugin",
                                         "maven-plugins/maven-plugin-plugin", "maven-plugins/maven-resources-plugin",
                                         "maven-plugins/maven-surefire-plugin"};

    private static final Map MODELLO_TARGET_VERSIONS;

    private static final Map MODELLO_MODEL_FILES;

    static
    {
        Map targetVersions = new TreeMap();
        targetVersions.put( "maven-model", "4.0.0" );
        targetVersions.put( "maven-settings", "1.0.0" );
        targetVersions.put( "maven-profile", "1.0.0" );
        targetVersions.put( "maven-plugin-registry", "1.0.0" );

        MODELLO_TARGET_VERSIONS = Collections.unmodifiableMap( targetVersions );

        Map modelFiles = new TreeMap();
        modelFiles.put( "maven-model", "maven.mdo" );
        modelFiles.put( "maven-settings", "settings.mdo" );
        modelFiles.put( "maven-profile", "profiles.mdo" );
        modelFiles.put( "maven-plugin-registry", "plugin-registry.mdo" );

        MODELLO_MODEL_FILES = Collections.unmodifiableMap( modelFiles );
    }

    // ----------------------------------------------------------------------
    // Standard locations for resources in Maven projects.
    // ----------------------------------------------------------------------

    private static final String SOURCES = "src/main/java";

    private static final String TEST_SOURCES = "src/test/java";

    private static final String RESOURCES = "src/main/resources";

    private static final String TEST_RESOURCES = "src/test/resources";

    private static final String BUILD_DIR = "target";

    private static final String CLASSES = BUILD_DIR + "/classes";

    private static final String TEST_CLASSES = BUILD_DIR + "/test-classes";

    private static final String GENERATED_SOURCES = BUILD_DIR + "/generated-sources";

    private static final String GENERATED_DOCS = BUILD_DIR + "/generated-docs";

    // ----------------------------------------------------------------------
    // Per-session entities which we can reuse while building many projects.
    // ----------------------------------------------------------------------

    private ArtifactDownloader downloader;

    private boolean online = true;

    // ----------------------------------------------------------------------
    //
    // ----------------------------------------------------------------------

    public static void main( String[] args )
        throws Exception
    {
        try
        {
            MBoot mboot = new MBoot();

            mboot.run( args );
        }
        catch ( InvocationTargetException e )
        {
            Throwable target = e.getTargetException();

            if ( target instanceof RuntimeException )
            {
                throw (RuntimeException) target;
            }
            else if ( target instanceof Exception )
            {
                throw (Exception) target;
            }
            else
            {
                throw new RuntimeException( target );
            }
        }
    }

    public void run( String[] args )
        throws Exception
    {
        String mavenRepoLocal = System.getProperty( "maven.repo.local" );

        SettingsReader userModelReader = new SettingsReader();

        String userHome = System.getProperty( "user.home" );

        File settingsXml = new File( userHome, ".m2/settings.xml" );

        if ( settingsXml.exists() )
        {
            userModelReader.parse( settingsXml );

            mavenRepoLocal = userModelReader.getLocalRepository();
        }

        if ( mavenRepoLocal == null )
        {
            String m2LocalRepoPath = "/.m2/repository";

            File repoDir = new File( userHome, m2LocalRepoPath );
            if ( !repoDir.exists() )
            {
                repoDir.mkdirs();
            }

            mavenRepoLocal = repoDir.getAbsolutePath();

            System.out.println(
                "You SHOULD have a ~/.m2/settings.xml file and must contain at least the following information:" );
            System.out.println();

            System.out.println( "<settings>" );
            System.out.println( "  <localRepository>/path/to/your/repository</localRepository>" );
            System.out.println( "</settings>" );

            System.out.println();

            System.out.println( "Alternatively, you can specify -Dmaven.repo.local=/path/to/m2/repository" );

            System.out.println();

            System.out.println( "HOWEVER, since you did not specify a repository path, maven will use: " +
                                repoDir.getAbsolutePath() + " to store artifacts locally." );
        }

        File repoLocalFile = new File( mavenRepoLocal );
        repoLocalFile.mkdirs();

        if ( !repoLocalFile.canWrite() )
        {
            System.err.println( "Can't write to " + mavenRepoLocal );

            System.exit( 1 );
        }

        String mavenHome = null;

        if ( args.length == 1 )
        {
            mavenHome = args[0];
        }
        else
        {
            mavenHome = System.getProperty( "maven.home" );

            if ( mavenHome == null )
            {
                mavenHome = new File( System.getProperty( "user.home" ), "m2" ).getAbsolutePath();
            }
        }

        File dist = new File( mavenHome );

        System.out.println( "Maven installation directory: " + dist );

        if ( dist.exists() )
        {
            if ( !new File( dist, "bin/m2.conf" ).exists() )
            {
                System.err.println( "The directory given to install Maven in already exists, but does not contain a Maven installation" );
                System.err.println();
                System.err.println( "\t" + dist );
                System.err.println();
                System.err.println( "If you really mean to install Maven to this location, please delete the directory first" );
                System.exit( 1 );
            }
        }

        Date fullStop;

        Date fullStart = new Date();

        String onlineProperty = System.getProperty( "maven.online" );

        if ( onlineProperty != null && onlineProperty.equals( "false" ) )
        {
            online = false;
        }

        Repository localRepository = new Repository( "local", mavenRepoLocal, Repository.LAYOUT_DEFAULT );

        if ( online )
        {
            downloader = new ArtifactDownloader( localRepository );
            if ( userModelReader.getActiveProxy() != null )
            {
                Proxy proxy = userModelReader.getActiveProxy();
                downloader.setProxy( proxy.getHost(), proxy.getPort(), proxy.getUserName(), proxy.getPassword() );
            }

            List remoteRepos = downloader.getRemoteRepositories();
            List newRemoteRepos = new ArrayList();

            for ( Iterator i = remoteRepos.iterator(); i.hasNext(); )
            {
                Repository repo = (Repository) i.next();

                boolean foundMirror = false;
                for ( Iterator j = userModelReader.getMirrors().iterator(); j.hasNext() && !foundMirror; )
                {
                    Mirror m = (Mirror) j.next();
                    if ( m.getMirrorOf().equals( repo.getId() ) )
                    {
                        newRemoteRepos.add( new Repository( m.getId(), m.getUrl(), repo.getLayout() ) );
                        foundMirror = true;
                    }
                }
                if ( !foundMirror )
                {
                    newRemoteRepos.add( repo );
                }
            }

            downloader.setRemoteRepositories( newRemoteRepos );

            System.out.println( "Using the following for your local repository: " + localRepository );
            System.out.println( "Using the following for your remote repository: " + newRemoteRepos );
        }

        String basedir = System.getProperty( "user.dir" );

        // Install maven-components POM
        installPomFile( localRepository, new File( basedir, "pom.xml" ) );

        // Install plugin-parent POM
        installPomFile( localRepository, new File( basedir, "maven-plugins/pom.xml" ) );

        // Install plugin-tools-parent POM
        installPomFile( localRepository, new File( basedir, "maven-plugin-tools/pom.xml" ) );

        // Install maven-script-parent POM
        installPomFile( localRepository, new File( basedir, "maven-script/pom.xml" ) );

        // Install maven-reporting parent POM
        installPomFile( localRepository, new File( basedir, "maven-reporting/pom.xml" ) );

        // Install it-support POM
        installPomFile( localRepository, new File( basedir, "maven-core-it-support/pom.xml" ) );

        ModelReader reader = new ModelReader( downloader, true );

        reader.parse( new File( basedir, "maven-mboot2/pom.xml" ) );

        ClassLoader bootstrapClassLoader = createClassloaderFromDependencies( reader.getDependencies(), null,
                                                                              localRepository );

        reader = new ModelReader( downloader, false );
        reader.parse( new File( basedir, "maven-plugins/maven-surefire-plugin/pom.xml" ) );
        List surefireDependencies = new ArrayList();

        // TODO: while we have maven-artifact in there, it needs to be filtered...
        for ( Iterator i = reader.getDependencies().iterator(); i.hasNext(); )
        {
            Dependency d = (Dependency) i.next();
            if ( d.getGroupId().equals( "surefire" ) || d.getGroupId().equals( "junit" ) )
            {
                surefireDependencies.add( d );
            }
        }

        if ( online )
        {
            downloader.downloadDependencies( surefireDependencies );
        }

        reader = new ModelReader( downloader, true );

        Collection coreDeps = null;
        Dependency corePom = null;

        for ( int i = 0; i < builds.length; i++ )
        {
            String directory = new File( basedir, builds[i] ).getAbsolutePath();

            System.out.println( "Building project in " + directory + " ..." );

            System.out.println( "--------------------------------------------------------------------" );

            System.setProperty( "basedir", directory );

            reader = buildProject( directory, builds[i], bootstrapClassLoader, surefireDependencies, localRepository );

            if ( reader.getArtifactId().equals( "maven-core" ) )
            {
                coreDeps = reader.getDependencies();
                corePom = new Dependency( reader.getGroupId(), reader.getArtifactId(), reader.getVersion(),
                                          reader.getPackaging() );
            }

            System.out.println( "--------------------------------------------------------------------" );
        }

        reader = new ModelReader( downloader, true );
        reader.parse( new File( basedir, "maven-plugin-tools/maven-plugin-tools-pluggy/pom.xml" ) );
        List dependencies = new ArrayList( reader.getDependencies() );
        dependencies.add(
            new Dependency( reader.getGroupId(), reader.getArtifactId(), reader.getVersion(), reader.getPackaging() ) );
        IsolatedClassLoader cl = createClassloaderFromDependencies( dependencies, bootstrapClassLoader,
                                                                    localRepository );

        for ( int i = 0; i < pluginBuilds.length; i++ )
        {
            String directory = new File( basedir, pluginBuilds[i] ).getAbsolutePath();

            System.out.println( "Building project in " + directory + " ..." );

            System.out.println( "--------------------------------------------------------------------" );

            System.setProperty( "basedir", directory );

            reader = buildProject( directory, pluginBuilds[i], cl, surefireDependencies, localRepository );

            System.out.println( "--------------------------------------------------------------------" );
        }

        // build the installation
        FileUtils.deleteDirectory( dist );

        // ----------------------------------------------------------------------
        // bin
        // ----------------------------------------------------------------------

        String bin = new File( dist, "bin" ).getAbsolutePath();

        FileUtils.mkdir( new File( bin ).getPath() );

        FileUtils.copyFileToDirectory( new File( basedir, "maven-core/src/bin/m2" ).getAbsolutePath(), bin );

        FileUtils.copyFileToDirectory( new File( basedir, "maven-core/src/bin/m2.bat" ).getAbsolutePath(), bin );

        FileUtils.copyFileToDirectory( new File( basedir, "maven-core/src/bin/m2.conf" ).getAbsolutePath(), bin );

        if ( Os.isFamily( "unix" ) )
        {
            Commandline cli = new Commandline();

            cli.setExecutable( "chmod" );

            cli.createArgument().setValue( "+x" );

            cli.createArgument().setValue( new File( dist, "bin/m2" ).getAbsolutePath() );

            cli.execute().waitFor();
        }

        // ----------------------------------------------------------------------
        // core
        // ----------------------------------------------------------------------

        File core = new File( dist, "core" );

        core.mkdirs();

        File boot = new File( dist, "core/boot" );

        boot.mkdirs();

        // ----------------------------------------------------------------------
        // lib
        // ----------------------------------------------------------------------

        File lib = new File( dist, "lib" );

        lib.mkdirs();

        for ( Iterator i = coreDeps.iterator(); i.hasNext(); )
        {
            Dependency d = (Dependency) i.next();

            File source = localRepository.getArtifactFile( d );
            if ( d.getArtifactId().equals( "classworlds" ) )
            {
                FileUtils.copyFileToDirectory( source, boot );
            }
            else if ( d.getArtifactId().equals( "plexus-container-default" ) 
                || d.getArtifactId().equals( "plexus-utils" ) )
            {
                FileUtils.copyFileToDirectory( source, core );
            }
            else if ( !d.getScope().equals( Dependency.SCOPE_TEST ) )
            {
                // only compile and runtime
                FileUtils.copyFileToDirectory( source, lib );
            }
        }

        // Copy maven itself
        FileUtils.copyFileToDirectory( localRepository.getArtifactFile( corePom ), lib );

        System.out.println();

        System.out.println( "Maven2 is installed in " + dist.getAbsolutePath() );

        System.out.println( "--------------------------------------------------------------------" );

        System.out.println();

        fullStop = new Date();

        stats( fullStart, fullStop );
    }

    protected static String formatTime( long ms )
    {
        long secs = ms / 1000;

        long min = secs / 60;
        secs = secs % 60;

        if ( min > 0 )
        {
            return min + " minutes " + secs + " seconds";
        }
        else
        {
            return secs + " seconds";
        }
    }

    private void stats( Date fullStart, Date fullStop )
    {
        long fullDiff = fullStop.getTime() - fullStart.getTime();

        System.out.println( "Total time: " + formatTime( fullDiff ) );

        System.out.println( "Finished at: " + fullStop );
    }

    private ModelReader buildProject( String basedir, String projectId, ClassLoader classLoader,
                                      List surefireDependencies, Repository localRepository )
        throws Exception
    {
        System.out.println( "Building project in " + basedir );

        ModelReader reader = new ModelReader( downloader, true );

        reader.parse( new File( basedir, "pom.xml" ) );

        String sources = new File( basedir, SOURCES ).getAbsolutePath();

        String resources = new File( basedir, RESOURCES ).getAbsolutePath();

        String classes = new File( basedir, CLASSES ).getAbsolutePath();

        String testSources = new File( basedir, TEST_SOURCES ).getAbsolutePath();

        String testResources = new File( basedir, TEST_RESOURCES ).getAbsolutePath();

        String testClasses = new File( basedir, TEST_CLASSES ).getAbsolutePath();

        String generatedSources = new File( basedir, GENERATED_SOURCES ).getAbsolutePath();

        File buildDirFile = new File( basedir, BUILD_DIR );
        String buildDir = buildDirFile.getAbsolutePath();

        // clean
        System.out.println( "Cleaning " + buildDirFile + "..." );
        FileUtils.forceDelete( buildDirFile );

        if ( online )
        {
            System.out.println( "Downloading project dependencies ..." );

            downloader.downloadDependencies( reader.getDependencies() );
        }

        // ----------------------------------------------------------------------
        // Generating sources
        // ----------------------------------------------------------------------

        File base = new File( basedir );

        String modelFileName = (String) MODELLO_MODEL_FILES.get( projectId );

        File model = null;
        if ( modelFileName != null && modelFileName.trim().length() > 0 )
        {
            model = new File( base, modelFileName );
        }

        if ( model != null && model.exists() )
        {
            System.out.println( "Model exists!" );

            String modelVersion = (String) MODELLO_TARGET_VERSIONS.get( projectId );
            if ( modelVersion == null || modelVersion.trim().length() < 1 )
            {
                System.out.println( "No model version configured. Using \'1.0.0\'..." );
                modelVersion = "1.0.0";
            }

            File generatedSourcesDirectory = new File( basedir, GENERATED_SOURCES );

            if ( !generatedSourcesDirectory.exists() )
            {
                generatedSourcesDirectory.mkdirs();
            }

            File generatedDocsDirectory = new File( basedir, GENERATED_DOCS );

            if ( !generatedDocsDirectory.exists() )
            {
                generatedDocsDirectory.mkdirs();
            }

            System.out.println(
                "Generating model bindings for version \'" + modelVersion + "\' in project: " + projectId );

            generateSources( model.getAbsolutePath(), "java", generatedSources, modelVersion, "false", classLoader );
            generateSources( model.getAbsolutePath(), "xpp3-reader", generatedSources, modelVersion, "false",
                             classLoader );
            generateSources( model.getAbsolutePath(), "xpp3-writer", generatedSources, modelVersion, "false",
                             classLoader );
        }

        // ----------------------------------------------------------------------
        // Standard compile
        // ----------------------------------------------------------------------

        System.out.println( "Compiling sources ..." );

        if ( new File( generatedSources ).exists() )
        {
            compile( reader.getDependencies(), sources, classes, null, generatedSources, Dependency.SCOPE_COMPILE,
                     localRepository );
        }
        else
        {
            compile( reader.getDependencies(), sources, classes, null, null, Dependency.SCOPE_COMPILE, localRepository );
        }

        // ----------------------------------------------------------------------
        // Plugin descriptor generation
        // ----------------------------------------------------------------------

        if ( reader.getPackaging().equals( "maven-plugin" ) )
        {
            System.out.println( "Generating maven plugin descriptor ..." );

            generatePluginDescriptor( sources, new File( classes, "META-INF/maven" ).getAbsolutePath(),
                                      new File( basedir, "pom.xml" ).getAbsolutePath(), classLoader, localRepository );
        }

        // ----------------------------------------------------------------------
        // Standard resources
        // ----------------------------------------------------------------------

        System.out.println( "Packaging resources ..." );

        copyResources( resources, classes );

        // ----------------------------------------------------------------------
        // Test compile
        // ----------------------------------------------------------------------

        System.out.println( "Compiling test sources ..." );

        Collection testDependencies = new ArrayList( reader.getDependencies() );

        compile( testDependencies, testSources, testClasses, classes, null, Dependency.SCOPE_TEST, localRepository );

        // ----------------------------------------------------------------------
        // Test resources
        // ----------------------------------------------------------------------

        System.out.println( "Packaging test resources ..." );

        copyResources( testResources, testClasses );

        // ----------------------------------------------------------------------
        // Run tests
        // ----------------------------------------------------------------------

        runTests( basedir, classes, testClasses, reader, surefireDependencies, localRepository );

        // ----------------------------------------------------------------------
        // Create JAR
        // ----------------------------------------------------------------------

        createJar( classes, buildDir, reader );

        installPom( basedir, localRepository, reader );

        install( basedir, localRepository, reader, reader.getPackaging() );

        return reader;
    }

    private void generatePluginDescriptor( String sourceDirectory, String outputDirectory, String pom, ClassLoader cl,
                                           Repository localRepository )
        throws Exception
    {
        Class cls = cl.loadClass( "org.apache.maven.tools.plugin.pluggy.Main" );

        Method m = cls.getMethod( "main", new Class[]{String[].class} );

        // Can pluggy really cope with the layout?
        String[] args = {"descriptor", sourceDirectory, outputDirectory, pom, localRepository.getBasedir()};

        m.invoke( null, new Object[]{args} );
    }

    private void generateSources( String model, String mode, String dir, String modelVersion,
                                  String packageWithVersion, ClassLoader modelloClassLoader )
        throws Exception
    {
        Class c = modelloClassLoader.loadClass( "org.codehaus.modello.ModelloCli" );

        Object generator = c.newInstance();

        Method m = c.getMethod( "main", new Class[]{String[].class} );

        String[] args = new String[]{model, mode, dir, modelVersion, packageWithVersion};

        ClassLoader old = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader( modelloClassLoader );

        m.invoke( generator, new Object[]{args} );

        Thread.currentThread().setContextClassLoader( old );
    }

    private IsolatedClassLoader createClassloaderFromDependencies( Collection dependencies, ClassLoader parent,
                                                                   Repository localRepository )
        throws Exception
    {
        if ( online )
        {
            System.out.println( "Checking for dependencies ..." );

            downloader.downloadDependencies( dependencies );
        }

        IsolatedClassLoader cl;
        if ( parent == null )
        {
            cl = new IsolatedClassLoader();
        }
        else
        {
            cl = new IsolatedClassLoader( parent );
        }

        for ( Iterator i = dependencies.iterator(); i.hasNext(); )
        {
            Dependency dependency = (Dependency) i.next();

            File f = localRepository.getArtifactFile( dependency );
            if ( !f.exists() )
            {
                String msg = ( !online ? "; run again online" : "; there was a problem downloading it earlier" );
                throw new FileNotFoundException( "Missing dependency: " + dependency + msg );
            }

            cl.addURL( f.toURL() );
        }

        return cl;
    }

    private void createJar( String classes, String buildDir, ModelReader reader )
        throws Exception
    {
        JarMojo jarMojo = new JarMojo();

        String artifactId = reader.getArtifactId();

        String version = reader.getVersion();

        jarMojo.execute( new File( classes ), buildDir, artifactId + "-" + version );
    }

    private void installPomFile( Repository localRepository, File pomIn )
        throws Exception
    {
        ModelReader reader = new ModelReader( downloader, false );

        reader.parse( pomIn );

        installPomFile( reader, localRepository, pomIn );
    }

    private void installPom( String basedir, Repository localRepository, ModelReader reader )
        throws Exception
    {
        installPomFile( reader, localRepository, new File( basedir, "pom.xml" ) );
    }

    private void installPomFile( ModelReader reader, Repository localRepository, File source )
        throws IOException
    {
        String artifactId = reader.getArtifactId();

        String version = reader.getVersion();

        String groupId = reader.getGroupId();

        File pom = localRepository.getMetadataFile( groupId, artifactId, version, reader.getPackaging(),
                                                    artifactId + "-" + version + ".pom" );

        System.out.println( "Installing POM: " + pom );

        FileUtils.copyFile( source, pom );
    }

    private void install( String basedir, Repository localRepository, ModelReader reader, String type )
        throws Exception
    {
        String artifactId = reader.getArtifactId();

        String version = reader.getVersion();

        String groupId = reader.getGroupId();

        String finalName = artifactId + "-" + version;

        File file = localRepository.getArtifactFile( groupId, artifactId, version, type );

        System.out.println( "Installing: " + file );

        if ( version.indexOf( "SNAPSHOT" ) >= 0 )
        {
            File metadata = localRepository.getMetadataFile( groupId, artifactId, version, type,
                                                             finalName + ".version.txt" );

            metadata.getParentFile().mkdirs();

            IOUtil.copy( new StringReader( version ), new FileWriter( metadata ) );
        }

        FileUtils.copyFile( new File( basedir, BUILD_DIR + "/" + finalName + ".jar" ), file );

        file = localRepository.getMetadataFile( groupId, artifactId, null, type, artifactId + "-RELEASE.version.txt" );
        IOUtil.copy( new StringReader( version ), new FileWriter( file ) );
    }

    private void runTests( String basedir, String classes, String testClasses, ModelReader reader,
                           List surefireDependencies, Repository localRepository )
        throws Exception
    {
        SurefirePlugin testRunner = new SurefirePlugin();

        List includes;

        List excludes;

        includes = new ArrayList();

        includes.add( "**/*Test.java" );

        excludes = new ArrayList();

        excludes.add( "**/Abstract*Test.java" );

        String reportsDir = new File( basedir, "target/surefire-reports" ).getAbsolutePath();

        List depList = new ArrayList( reader.getDependencies() );
        depList.addAll( surefireDependencies );

        List classpath = classpath( depList, null, Dependency.SCOPE_TEST, localRepository );
        classpath.add( classes );
        classpath.add( testClasses );

        boolean success = testRunner.execute( basedir, includes, excludes, classpath, reportsDir );

        if ( !success )
        {
            throw new Exception( "Tests error" );
        }
    }

    // ----------------------------------------------------------------------
    // Compile
    // ----------------------------------------------------------------------

    private List classpath( Collection dependencies, String extraClasspath, String scope, Repository localRepository )
    {
        List classpath = new ArrayList( dependencies.size() + 1 );

        for ( Iterator i = dependencies.iterator(); i.hasNext(); )
        {
            Dependency d = (Dependency) i.next();

            String element = localRepository.getArtifactFile( d ).getAbsolutePath();

            if ( Dependency.SCOPE_COMPILE.equals( scope ) )
            {
                if ( d.getScope().equals( Dependency.SCOPE_COMPILE ) )
                {
                    classpath.add( element );
                }
            }
            else if ( Dependency.SCOPE_RUNTIME.equals( scope ) )
            {
                if ( d.getScope().equals( Dependency.SCOPE_COMPILE ) || d.getScope().equals( Dependency.SCOPE_RUNTIME ) )
                {
                    classpath.add( element );
                }
            }
            else if ( Dependency.SCOPE_TEST.equals( scope ) )
            {
                classpath.add( element );
            }
        }

        if ( extraClasspath != null )
        {
            classpath.add( extraClasspath );
        }

        return classpath;
    }

    private void compile( Collection dependencies, String sourceDirectory, String outputDirectory,
                          String extraClasspath, String generatedSources, String scope, Repository localRepository )
        throws Exception
    {
        JavacCompiler compiler = new JavacCompiler();

        String[] sourceDirectories = null;

        if ( generatedSources != null )
        {
            // We might only have generated sources

            if ( new File( sourceDirectory ).exists() )
            {
                sourceDirectories = new String[]{sourceDirectory, generatedSources};
            }
            else
            {
                sourceDirectories = new String[]{generatedSources};
            }
        }
        else
        {
            if ( new File( sourceDirectory ).exists() )
            {
                sourceDirectories = new String[]{sourceDirectory};
            }
        }

        if ( sourceDirectories != null )
        {
            CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
            compilerConfiguration.setOutputLocation( outputDirectory );
            List classpathEntries = classpath( dependencies, extraClasspath, scope, localRepository );
            compilerConfiguration.setNoWarn( true );
            compilerConfiguration.setClasspathEntries( classpathEntries );
            compilerConfiguration.setSourceLocations( Arrays.asList( sourceDirectories ) );

            /* Compile with debugging info */
            String debugAsString = System.getProperty( "maven.compiler.debug", "true" );

            if ( !Boolean.valueOf( debugAsString ).booleanValue() )
            {
                compilerConfiguration.setDebug( false );
            }
            else
            {
                compilerConfiguration.setDebug( true );
            }

            List messages = compiler.compile( compilerConfiguration );

            for ( Iterator i = messages.iterator(); i.hasNext(); )
            {
                System.out.println( i.next() );
            }

            if ( messages.size() > 0 )
            {
                throw new Exception( "Compilation error." );
            }
        }
    }

    // ----------------------------------------------------------------------
    // model.Resource copying
    // ----------------------------------------------------------------------

    private void copyResources( String sourceDirectory, String destinationDirectory )
        throws Exception
    {
        File sd = new File( sourceDirectory );

        if ( !sd.exists() )
        {
            return;
        }

        List files = FileUtils.getFiles( sd, "**/**", "**/CVS/**,**/.svn/**", false );

        for ( Iterator i = files.iterator(); i.hasNext(); )
        {
            File f = (File) i.next();

            File source = new File( sourceDirectory, f.getPath() );

            File dest = new File( destinationDirectory, f.getPath() );

            if ( !dest.getParentFile().exists() )
            {
                dest.getParentFile().mkdirs();
            }

            FileUtils.copyFile( source, dest );
        }
    }

    class SettingsReader
        extends AbstractReader
    {
        private List mirrors = new ArrayList();

        private List proxies = new ArrayList();

        private Proxy currentProxy = null;

        private StringBuffer currentBody = new StringBuffer();

        private Proxy activeProxy = null;

        private Mirror currentMirror;
        
        private String localRepository;
        
        public String getLocalRepository()
        {
            return localRepository;
        }

        public Proxy getActiveProxy()
        {
            return activeProxy;
        }

        public void characters( char[] ch, int start, int length )
            throws SAXException
        {
            currentBody.append( ch, start, length );
        }

        public void endElement( String uri, String localName, String rawName )
            throws SAXException
        {
            if ( "localRepository".equals( rawName ) )
            {
                if ( notEmpty( currentBody.toString() ) )
                {
                    localRepository = currentBody.toString().trim();
                }
                else
                {
                    throw new SAXException( "Invalid profile entry. Missing one or more " +
                                            "fields: {localRepository}." );
                }
            }
            else if ( "proxy".equals( rawName ) )
            {
                if ( notEmpty( currentProxy.getHost() ) && notEmpty( currentProxy.getPort() ) )
                {
                    proxies.add( currentProxy );
                    currentProxy = null;
                }
                else
                {
                    throw new SAXException( "Invalid proxy entry. Missing one or more fields: {host, port}." );
                }
            }
            else if ( currentProxy != null )
            {
                if ( "active".equals( rawName ) )
                {
                    currentProxy.setActive( Boolean.valueOf( currentBody.toString().trim() ).booleanValue() );
                }
                else if ( "host".equals( rawName ) )
                {
                    currentProxy.setHost( currentBody.toString().trim() );
                }
                else if ( "port".equals( rawName ) )
                {
                    currentProxy.setPort( currentBody.toString().trim() );
                }
                else if ( "username".equals( rawName ) )
                {
                    currentProxy.setUserName( currentBody.toString().trim() );
                }
                else if ( "password".equals( rawName ) )
                {
                    currentProxy.setPassword( currentBody.toString().trim() );
                }
                else if ( "protocol".equals( rawName ) )
                {
                }
                else if ( "nonProxyHosts".equals( rawName ) )
                {
                }
                else
                {
                    throw new SAXException( "Illegal element inside proxy: \'" + rawName + "\'" );
                }
            }
            else if ( "mirror".equals( rawName ) )
            {
                if ( notEmpty( currentMirror.getId() ) && notEmpty( currentMirror.getMirrorOf() ) &&
                    notEmpty( currentMirror.getUrl() ) )
                {
                    mirrors.add( currentMirror );
                    currentMirror = null;
                }
                else
                {
                    throw new SAXException( "Invalid mirror entry. Missing one or more fields: {id, mirrorOf, url}." );
                }
            }
            else if ( currentMirror != null )
            {
                if ( "id".equals( rawName ) )
                {
                    currentMirror.setId( currentBody.toString().trim() );
                }
                else if ( "mirrorOf".equals( rawName ) )
                {
                    currentMirror.setMirrorOf( currentBody.toString().trim() );
                }
                else if ( "url".equals( rawName ) )
                {
                    currentMirror.setUrl( currentBody.toString().trim() );
                }
                else if ( "name".equals( rawName ) )
                {
                }
                else
                {
                    throw new SAXException( "Illegal element inside proxy: \'" + rawName + "\'" );
                }
            }
            else if ( "settings".equals( rawName ) )
            {
                if ( proxies.size() != 0 )
                {
                    for ( Iterator it = proxies.iterator(); it.hasNext(); )
                    {
                        Proxy proxy = (Proxy) it.next();
                        if ( proxy.isActive() )
                        {
                            activeProxy = proxy;
                        }
                    }
                }
            }

            currentBody = new StringBuffer();
        }

        private boolean notEmpty( String test )
        {
            return test != null && test.trim().length() > 0;
        }

        public void startElement( String uri, String localName, String rawName, Attributes attributes )
            throws SAXException
        {
            if ( "proxy".equals( rawName ) )
            {
                currentProxy = new Proxy();
            }
            else if ( "mirror".equals( rawName ) )
            {
                currentMirror = new Mirror();
            }
        }

        public void reset()
        {
            this.currentBody = null;
            this.activeProxy = null;
            this.currentMirror = null;
            this.localRepository = null;
            this.proxies.clear();
            this.mirrors.clear();
        }

        public List getMirrors()
        {
            return mirrors;
        }
    }

    public static class Proxy
    {
        private boolean active;

        private String host;

        private String port;

        private String userName;

        private String password;

        public boolean isActive()
        {
            return active;
        }

        public void setActive( boolean active )
        {
            this.active = active;
        }

        public void setHost( String host )
        {
            this.host = host;
        }

        public String getHost()
        {
            return host;
        }

        public void setPort( String port )
        {
            this.port = port;
        }

        public String getPort()
        {
            return port;
        }

        public void setUserName( String userName )
        {
            this.userName = userName;
        }

        public String getUserName()
        {
            return userName;
        }

        public void setPassword( String password )
        {
            this.password = password;
        }

        public String getPassword()
        {
            return password;
        }
    }

    public static class Mirror
    {
        private String id;

        private String mirrorOf;

        private String url;

        public String getId()
        {
            return id;
        }

        public void setId( String id )
        {
            this.id = id;
        }

        public void setMirrorOf( String mirrorOf )
        {
            this.mirrorOf = mirrorOf;
        }

        public void setUrl( String url )
        {
            this.url = url;
        }

        public String getMirrorOf()
        {
            return mirrorOf;
        }

        public String getUrl()
        {
            return url;
        }
    }
}
