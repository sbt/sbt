@echo on

cd "%~dp0"

mkdir freshly-baked
unzip ..\target\universal\sbt.zip -d freshly-baked

SETLOCAL

"freshly-baked\sbt\bin\sbt" about

SET JAVA_HOME=C:\jdk11
SET PATH=C:\jdk11\bin;%PATH%
SET SBT_OPTS=-Xmx4g -Dfile.encoding=UTF8

"freshly-baked\sbt\bin\sbt" "-Dsbt.no.format=true" about

ENDLOCAL
