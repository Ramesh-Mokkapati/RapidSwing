@echo off
rem Compiles RapidSwing, runs the tests and packages the runnable jars of the demo and the generator.
rem
rem   build                  full build with tests
rem   build --skip-tests     faster: compile and package only
rem   build --offline        use only the local Maven repository
rem   build -- <args>        pass further arguments to Maven
rem
rem Maven: %MAVEN_CMD%, else mvn on the PATH, else a Maven wrapper download in %USERPROFILE%\.m2\wrapper.
rem Java:  %JAVA_HOME%, else java on the PATH. Java 8 or newer.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "MAVEN_ARGS=clean install"
rem The arguments as typed: cmd's own splitting would break -Dname=value at the "=".
set "ALL_ARGS=%*"
:parse
if "%~1"=="" goto parsed
if "%~1"=="--skip-tests" (set "MAVEN_ARGS=!MAVEN_ARGS! -DskipTests" & shift & goto parse)
if "%~1"=="--offline" (set "MAVEN_ARGS=!MAVEN_ARGS! -o" & shift & goto parse)
if "%~1"=="--" goto rest
if "%~1"=="-h" goto help
if "%~1"=="--help" goto help
echo Unknown option: %~1 (see --help) 1>&2
exit /b 2
:rest
rem Everything after "-- " goes to Maven unchanged.
set "REST_ARGS= !ALL_ARGS! "
set "REST_ARGS=!REST_ARGS:* -- =!"
set "MAVEN_ARGS=!MAVEN_ARGS! !REST_ARGS!"
goto parsed
:help
echo Compiles RapidSwing, runs the tests and packages the runnable jars of the demo and the generator.
echo.
echo   build                  full build with tests
echo   build --skip-tests     faster: compile and package only
echo   build --offline        use only the local Maven repository
echo   build -- ^<args^>        pass further arguments to Maven
echo.
echo Maven: %%MAVEN_CMD%%, else mvn on the PATH, else a Maven wrapper download in %%USERPROFILE%%\.m2\wrapper.
echo Java:  %%JAVA_HOME%%, else java on the PATH. Java 8 or newer.
exit /b 0
:parsed

set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo Java not found. Install Java 8 or newer, or set JAVA_HOME. 1>&2
    exit /b 1
)

set "MAVEN="
if defined MAVEN_CMD (
    set "MAVEN=%MAVEN_CMD%"
) else (
    for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MAVEN set "MAVEN=%%M"
    if not defined MAVEN (
        for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*") do (
            for /d %%H in ("%%D\*") do (
                for /d %%V in ("%%H\apache-maven-*") do (
                    if exist "%%V\bin\mvn.cmd" set "MAVEN=%%V\bin\mvn.cmd"
                )
            )
        )
    )
)
if not defined MAVEN (
    echo Maven not found. Install Maven 3.6 or newer, put mvn on the PATH, or set MAVEN_CMD. 1>&2
    exit /b 1
)

echo Using %MAVEN%
"%JAVA_CMD%" -version
call "%MAVEN%" %MAVEN_ARGS%
if errorlevel 1 exit /b 1

echo.
echo Built:
dir /b /s rapidswing-demo\target\*-app.jar rapidswing-generator\target\*-app.jar
echo Start them with "run demo" or "run generator"
exit /b 0
