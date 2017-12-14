cd "%~dp0"

SET JAVA_HOME=C:\jdk9
SET PATH=C:\jdk9\bin;%PATH%

"..\target\freshly-baked\sbt\bin\sbt" about
