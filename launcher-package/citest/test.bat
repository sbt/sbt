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

rem Regression test for https://github.com/sbt/sbt/issues/9660, run as a real
rem .bat command (parsed by cmd.exe the same way as if typed at a prompt),
rem rather than via a JVM-constructed command line.
"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true "eval (\"bar\") ++ (\"qux\")" 1> evalOutput.txt 2> evalErr.txt

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true "eval (\"foo\") & echo INJECTED>injected.txt & rem (" 1> evalInjOutput.txt 2> evalInjErr.txt

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkEvalArgHandling

rem "about" comes before the risky argument so sbt.bat always receives a real
rem command and cannot fall into an interactive shell (which would hang this
rem script) even if the & below does escape its quoting.
"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true about "-Dfoo=()&copy nul injected2.txt"

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkDArgHandling

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true about "-XXbar=()&copy nul injected3.txt"

"freshly-baked\sbt\bin\sbt" -Dsbt.no.format=true checkXXArgHandling

ENDLOCAL
