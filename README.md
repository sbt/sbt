sbt: the rebel cut
==================

An alternative script for running [sbt 0.10](https://github.com/harrah/xsbt).
There's also a template project sbt coming together, but it's unfinished.
However the runner is quite useful already.

    ./sbt -help

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

