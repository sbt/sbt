@echo on

cd "%~dp0"

mkdir freshly-baked
unzip ..\target\universal\sbt.zip -d freshly-baked

SETLOCAL

"freshly-baked\sbt\bin\sbt" about

SET JAVA_HOME=C:\jdk11
SET PATH=C:\jdk11\bin;%PATH%
SET SBT_OPTS=-Xmx4g -Dfile.encoding=UTF8

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true about

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true about 1> output.txt 2> err.txt

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true check

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true --numeric-version > numericVersion.txt
"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkNumericVersion

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true --script-version > scriptVersion.txt
"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkScriptVersion

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true --version > version.txt
"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkVersion

ENDLOCAL
