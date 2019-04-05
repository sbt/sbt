sbt: the launcher script
========================

This is a launcher script for running [sbt](https://github.com/sbt/sbt).

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
  /etc/sbt/sbtopts   if this file exists, it is prepended to the runner args
  -Dkey=val          pass -Dkey=val directly to the java runtime
  -J-X               pass option -X directly to the java runtime
                     (-J is stripped)
  -S-X               add -X to sbt's scalacOptions (-S is stripped)
```

### Native packages

This project also includes native packages to run sbt for

* Windows
* RedHat (rpm)
* Debian (deb)

Locations for download to be available soon.

### Build status

[![Build Status](https://travis-ci.org/sbt/sbt-launcher-package.svg?branch=master)](https://travis-ci.org/sbt/sbt-launcher-package)
[![Build Status](https://ci.appveyor.com/api/projects/status/github/sbt/sbt-launcher-package?branch=master&svg=true&retina=true)](https://ci.appveyor.com/project/sbt/sbt-launcher-package)
