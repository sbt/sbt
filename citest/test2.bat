@echo on

SETLOCAL

SET JAVA_HOME=C:\jdk10
SET PATH=C:\jdk10\bin;%PATH%
SET SBT_OPTS=-Xmx4g -Dfile.encoding=UTF8

"freshly-baked\sbt\bin\sbt" check

ENDLOCAL
