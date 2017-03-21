@REM https://github.com/xerial/sbt-pack/blob/master/src/main/templates/launch-bat.mustache
@REM would be worth getting more inspiration from

@echo off

SET ERROR_CODE=0

SET LAUNCHER_PATH=%~dp0/coursier

IF NOT EXIST %LAUNCHER_PATH% (
  bitsadmin /transfer "DownloadCoursierLauncher" https://github.com/coursier/coursier/raw/master/coursier %LAUNCHER_PATH%
)

SET CMD_LINE_ARGS=%*

java -jar %LAUNCHER_PATH% %CMD_LINE_ARGS%

IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=1

:end
SET LAUNCHER_PATH=
SET CMD_LINE_ARGS=

EXIT /B %ERROR_CODE%

