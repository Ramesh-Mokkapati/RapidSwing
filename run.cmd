@echo off
rem Starts a RapidSwing application, building it first if it has not been built yet.
rem
rem   run demo [options]         the demo app, e.g. run demo --app.theme=dark --app.locale=de
rem   run generator              the application generator wizard
rem   run generator <options>    the generator's command line (see run generator --help)
rem
rem Java: %JAVA_HOME%, else java on the PATH. Extra JVM options can be set in %JAVA_OPTS%.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "APP=%~1"
rem The arguments as typed: cmd's own splitting would break --app.theme=dark at the "=".
set "APP_ARGS=%*"
if "%APP%"=="demo" (
    set "JAR_DIR=rapidswing-demo\target"
    set "JAR_GLOB=rapidswing-demo-*-app.jar"
) else if "%APP%"=="generator" (
    set "JAR_DIR=rapidswing-generator\target"
    set "JAR_GLOB=rapidswing-generator-*-app.jar"
) else if "%APP%"=="" (
    goto help
) else if "%APP%"=="-h" (
    goto help
) else if "%APP%"=="--help" (
    goto help
) else (
    echo Unknown application: %APP% ^(use demo or generator^) 1>&2
    exit /b 2
)
rem Drop the application name; the rest goes to the application unchanged.
set "APP_ARGS=!APP_ARGS:*%APP%=!"

call :findjar
if not defined JAR (
    echo The %APP% has not been built yet; building it now ^(without tests^).
    call "%~dp0build.cmd" --skip-tests
    if errorlevel 1 exit /b 1
    call :findjar
)

set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR%" %APP_ARGS%
exit /b %ERRORLEVEL%

:findjar
set "JAR="
for %%J in ("%JAR_DIR%\%JAR_GLOB%") do if not defined JAR set "JAR=%%~fJ"
exit /b 0

:help
echo Starts a RapidSwing application, building it first if it has not been built yet.
echo.
echo   run demo [options]         the demo app, e.g. run demo --app.theme=dark --app.locale=de
echo   run generator              the application generator wizard
echo   run generator ^<options^>    the generator's command line (see run generator --help)
echo.
echo Java: %%JAVA_HOME%%, else java on the PATH. Extra JVM options can be set in %%JAVA_OPTS%%.
exit /b 0
