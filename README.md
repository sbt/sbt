sbt: the rebel cut
==================

An alternative script for running [sbt 0.10](https://github.com/harrah/xsbt).
There's also a template project sbt coming together, but it's unfinished.
However the runner is quite useful already.

Here's a sample first run, which uses a shared boot directory and a local scala.

    % ./sbt -shared /tmp/bippy -local /scala/inst/3 run
    Downloading sbt launcher, this should only take a moment...
      % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                     Dload  Upload   Total   Spent    Left  Speed
    100  915k  100  915k    0     0   339k      0  0:00:02  0:00:02 --:--:--  354k
    [info] Set current project to project-name-here (in build file:/repo/sbt-template/)
    [info] Running template.Main 
    Skeleton main, reporting for duty on version 2.10.0.r25487-b20110811131227
    [success] Total time: 0 s, completed Aug 14, 2011 11:12:58 AM

Adding -debug to the same command line reveals the command line arguments:

    % ./sbt -shared /tmp/bippy -local /scala/inst/3 run
    # Executing command line:
    java
    -Xss2m
    -Xms256M
    -Xmx1536M
    -XX:MaxPermSize=256M
    -XX:+CMSClassUnloadingEnabled
    -XX:MaxPermSize=512m
    -Xmx2g
    -Xss2m
    -Dsbt.boot.directory=/tmp/bippy
    -jar
    /repo/sbt-template/lib/sbt-launch.jar
    "set logLevel := Level.Debug"
    "set scalaHome := Some(file("/scala/inst/3"))"
    run

Current -help output:

    % ./sbt -help

    Usage: sbt [options]

      -help           prints this message
      -nocolor        disable ANSI color codes
      -debug          set sbt log level to debug
      -sbtdir <path>  location of global settings and plugins (default: ~/.sbt)
         -ivy <path>  local Ivy repository (default: ~/.ivy2)
      -shared <path>  shared sbt boot directory (default: none, no sharing)

      # setting scala version
      -28           set scala version to 2.8.1
      -29           set scala version to 2.9.0-1
      -210          set scala version to 2.10.0-SNAPSHOT
      -local <path> set scala version to local installation at path

      # passing options to jvm
      JAVA_OPTS     environment variable  # default: "-Dfile.encoding=UTF8"
      SBT_OPTS      environment variable  # default: "-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xmx2g -Xss2m"
      -Dkey=val     pass -Dkey=val directly to the jvm
      -J-X          pass option -X directly to the jvm (-J is stripped)

    The defaults given for JAVA_OPTS and SBT_OPTS are only used if the
    corresponding variable is unset. In the case of a duplicated option,
    SBT_OPTS takes precedence over JAVA_OPTS, and command line options
    take precedence over both.

