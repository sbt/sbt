@echo off
set SBT_HOME=%~dp0
java -Xmx512M -Dsbt.log.format=true -cp "%SBT_HOME%jansi.jar;%SBT_HOME%sbt-launch.jar;%SBT_HOME%classes" SbtJansiLaunch %*
