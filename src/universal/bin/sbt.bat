@REM SBT launcher script
@REM 
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (mandatory)
@REM SBT_OPTS  - JVM options (optional)
@REM Configuration:
@REM sbtconfig.txt found in the SBT_HOME.

@REM   ZOMG! We need delayed expansion to build up CFG_OPTS later 
@setlocal enabledelayedexpansion

@echo off
set SBT_HOME=%~dp0

rem FIRST we load the config file of extra options.
set FN=%SBT_HOME%\..\conf\sbtconfig.txt
set CFG_OPTS=
FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%FN%") DO (
  set DO_NOT_REUSE_ME=%%i
  rem ZOMG (Part #2) WE use !! here to delay the expansion of
  rem CFG_OPTS, otherwise it remains "" for this loop.
  set CFG_OPTS=!CFG_OPTS! !DO_NOT_REUSE_ME!
)

rem We use the value of the JAVACMD environment variable if defined
set _JAVACMD=%JAVACMD%

if "%_JAVACMD%"=="" (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "%_JAVA_OPTS%"=="" set _JAVA_OPTS=%CFG_OPTS%

FOR %%a IN (%*) DO (
  if "%%a" == "-jvm-debug" (
    set JVM_DEBUG=true
    set /a JVM_DEBUG_PORT=5005 2>nul >nul
  ) else if "!JVM_DEBUG!" == "true" (
    set /a JVM_DEBUG_PORT=%%a 2>nul >nul
    if not "%%a" == "!JVM_DEBUG_PORT!" (
      set SBT_ARGS=!SBT_ARGS! %%a
    )
  ) else (
    set SBT_ARGS=!SBT_ARGS! %%a
  )
)

if defined JVM_DEBUG_PORT (
  set _JAVA_OPTS=!_JAVA_OPTS! -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=!JVM_DEBUG_PORT!
)

call :run %SBT_ARGS%

if ERRORLEVEL 1 goto error
goto end

:run

"%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -cp "%SBT_HOME%sbt-launch.jar" xsbt.boot.Boot %*
goto :eof

:error
@endlocal
exit /B 1


:end
@endlocal
exit /B 0
