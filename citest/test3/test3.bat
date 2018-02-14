@echo on

SETLOCAL

SET JAVA_HOME=C:\jdk9
SET PATH=C:\jdk9\bin;%PATH%
SET SBT_OPTS=-Xmx4g -Dfile.encoding=UTF8

SET BASE_DIR=%CD%
SET SCRIPT_DIR=%~dp0

CD %SCRIPT_DIR%
"%BASE_DIR%freshly-baked\sbt\bin\sbt" about 1> output.txt 2> err.txt
"%BASE_DIR%freshly-baked\sbt\bin\sbt" check
CD %BASE_DIR%

ENDLOCAL
