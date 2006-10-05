@REM ----------------------------------------------------------------------------
@REM Copyright 2001-2004 The Apache Software Foundation.
@REM 
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM 
@REM      http://www.apache.org/licenses/LICENSE-2.0
@REM 
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM ----------------------------------------------------------------------------
@REM 

@REM ----------------------------------------------------------------------------
@REM Maven2 Bootstrap Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM M2_HOME - location of maven2's installed home dir
@REM
@REM Optional ENV vars
@REM MAVEN_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
@REM MAVEN_BATCH_PAUSE - set to 'on' to wait for a key stroke before ending
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven2 bootstrap
@REM     e.g. to run in offline mode, use
@REM set MAVEN_OPTS=-Dmaven.online=false
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM enable echoing my setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

echo.
echo ERROR: JAVA_HOME not found in your environment.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto end

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" goto chkMHome

echo.
echo ERROR: JAVA_HOME is set to an invalid directory.
echo JAVA_HOME = %JAVA_HOME%
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation
echo.
goto end

:chkMHome
if not "%M2_HOME%"=="" goto init

echo.
echo ERROR: M2_HOME not found in your environment.
echo Please set the M2_HOME variable in your environment to match the
echo location of the Maven installation
echo.
goto end
@REM ==== END VALIDATION ====

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set MAVEN_CMD_LINE_ARGS=%*
goto endInit

@REM The 4NT Shell from jp software
:4NTArgs
set MAVEN_CMD_LINE_ARGS=%$
goto endInit

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of agruments (up to the command line limit, anyway).
set MAVEN_CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto endInit
set MAVEN_CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS% %1
shift
goto Win9xApp

@REM Reaching here means variables are defined and arguments have been captured
:endInit
SET MAVEN_JAVA_EXE="%JAVA_HOME%\bin\java.exe"

@REM Build MBoot2
cd bootstrap\bootstrap-mini

call .\build

copy target\bootstrap-mini.jar ..
%MAVEN_JAVA_EXE% %MAVEN_OPTS% -Djava.compiler=NONE -jar ..\bootstrap-mini.jar install %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error

cd ..\bootstrap-installer
%MAVEN_JAVA_EXE% %MAVEN_OPTS% -jar ..\bootstrap-mini.jar package %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
cd ..\..

set PLUGINS_DIR=..\plugins
if exist "%PLUGINS_DIR%\pom.xml" goto setArgs

set BUILD_ARGS=%MAVEN_CMD_LINE_ARGS%
goto doBuild

:setArgs
set BUILD_ARGS=%MAVEN_CMD_LINE_ARGS% --build-plugins --plugins-directory=%PLUGINS_DIR%

:doBuild

REM TODO: get rid of M2_HOME once integration tests are in here
set DESTDIR=%M2_HOME%
set OLD_M2_HOME=%M2_HOME%
set M2_HOME=
%MAVEN_JAVA_EXE% %MAVEN_OPTS% -jar bootstrap\bootstrap-installer\target\bootstrap-installer.jar --destDir=%DESTDIR% %BUILD_ARGS%
REM %MAVEN_JAVA_EXE% %MAVEN_OPTS% -Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000 -jar bootstrap\bootstrap-installer\target\bootstrap-installer.jar --destDir=%DESTDIR% %BUILD_ARGS%

set M2_HOME=%OLD_M2_HOME%
if ERRORLEVEL 1 goto error

REM TODO: should we be going back to the mini now that we have the real thing?
cd maven-core-it-verifier
%MAVEN_JAVA_EXE% %MAVEN_OPTS% -jar ..\bootstrap\bootstrap-mini.jar package %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error

cd ..

del bootstrap\bootstrap-mini.jar

echo
echo -----------------------------------------------------------------------
echo Running integration tests
echo -----------------------------------------------------------------------
cd maven-core-it
call maven-core-it %MAVEN_CMD_LINE_ARGS%
cd ..

if ERRORLEVEL 1 goto error
goto end

:error
echo -----------------------------------------------------------------------
echo BUILD FAILED
echo -----------------------------------------------------------------------


:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set MAVEN_JAVA_EXE=
set MAVEN_CMD_LINE_ARGS=
goto postExec

:endNT
@endlocal

:postExec
@REM pause the batch file if MAVEN_BATCH_PAUSE is set to 'on'
if "%MAVEN_BATCH_PAUSE%" == "on" pause

