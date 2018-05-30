# Build Status

[![Build Status](https://travis-ci.org/sbt/sbt-launcher-package.svg?branch=master)](https://travis-ci.org/sbt/sbt-launcher-package)
[![Build Status](https://ci.appveyor.com/api/projects/status/github/sbt/sbt-launcher-package?branch=master&svg=true&retina=true)](https://ci.appveyor.com/project/sbt/sbt-launcher-package)

sbt: the launcher
==================

An alternative script for running [sbt](https://github.com/sbt/sbt).
It works with sbt 0.7.x projects as well as 0.10+. If you're in an sbt
project directory, the runner will figure out the versions of sbt and
scala required by the project and download them if necessary.

Sample usage: create a new project using a snapshot version of sbt as
well as a snapshot version of scala, then run the sbt "about" command.

    % sbt -v -sbt-snapshot -210 -sbt-create about
    Detected sbt version 0.11.3-SNAPSHOT
    sbt snapshot is 0.11.3-20111207-052114
    # Executing command line:
    java
    -XX:+CMSClassUnloadingEnabled
    -Xms1536m
    -Xmx1536m
    -XX:MaxPermSize=384m
    -XX:ReservedCodeCacheSize=192m
    -Dfile.encoding=UTF8
    -jar
    /r/sbt-extras/.lib/0.11.3-SNAPSHOT/sbt-launch.jar
    "set resolvers in ThisBuild += ScalaToolsSnapshots"
    "++ 2.10.0-SNAPSHOT"
    about

    [info] Loading global plugins from /Users/paulp/.sbt/plugins
    [info] Set current project to default-71999b (in build file:/Users/paulp/Desktop/new/)
    [info] Reapplying settings...
    [info] Set current project to default-71999b (in build file:/Users/paulp/Desktop/new/)
    Setting version to 2.10.0-SNAPSHOT
    [info] Set current project to default-71999b (in build file:/Users/paulp/Desktop/new/)
    [info] This is sbt 0.11.3-20111207-052114
    [info] The current project is {file:/Users/paulp/Desktop/new/}default-71999b
    [info] The current project is built against Scala 2.10.0-SNAPSHOT
    [info] sbt, sbt plugins, and build definitions are using Scala 2.9.1

Current -help output:
```bash
Usage: sbt [options]

  -h | -help         print this message
  -v | -verbose      this runner is chattier
  -d | -debug        set sbt log level to debug
  -no-colors         disable ANSI color codes
  -sbt-create        start sbt even if current directory contains no sbt project
  -sbt-dir   <path>  path to global settings/plugins directory (default: ~/.sbt)
  -sbt-boot  <path>  path to shared boot directory (default: ~/.sbt/boot in 0.11 series)
  -ivy       <path>  path to local Ivy repository (default: ~/.ivy2)
  -mem    <integer>  set memory options (default: 1024, which is -Xms1024m -Xmx1024m -XX:ReservedCodeCacheSize=128m -XX:MaxMetaspaceSize=256m)
  -no-share          use all local caches; no sharing
  -no-global         uses global caches, but does not use global ~/.sbt directory.
  -jvm-debug <port>  Turn on JVM debugging, open at the given port.
  -batch             Disable interactive mode

  # sbt version (default: from project/build.properties if present, else latest release)
  -sbt-version  <version>   use the specified version of sbt
  -sbt-jar      <path>      use the specified jar as the sbt launcher
  -sbt-rc                   use an RC version of sbt
  -sbt-snapshot             use a snapshot version of sbt

  # java version (default: java from PATH, currently openjdk version "1.8.0_172")
  -java-home <path>         alternate JAVA_HOME

  # jvm options and output control
  JAVA_OPTS          environment variable, if unset uses ""
  .jvmopts           if this file exists in the current directory, its contents
                     are appended to JAVA_OPTS
  SBT_OPTS           environment variable, if unset uses ""
  .sbtopts           if this file exists in the current directory, its contents
                     are prepended to the runner args
  /usr/local/etc/sbtopts   if this file exists, it is prepended to the runner args
  -Dkey=val          pass -Dkey=val directly to the java runtime
  -J-X               pass option -X directly to the java runtime
                     (-J is stripped)
  -S-X               add -X to sbt's scalacOptions (-S is stripped)
```

## Native Packages ##

This project also includes native packages to run SBT for 

* Windows
* RedHat (rpm)
* Debian (deb)
* Homebrew (coming soon!)

Locations for download to be available soon.

