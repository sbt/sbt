sbt: the rebel cut
==================

An alternative script for running [sbt 0.10+](https://github.com/harrah/xsbt).
There's also a template project sbt coming together, but it's unfinished.
However the runner is quite useful already.

Here's a sample first run, which creates a new project using a snapshot
version of sbt, and runs the sbt "about" command.

    % sbt -debug -snapshot -create about
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
    -jar
    /r/sbt-extras/.lib/0.11.0-20110825-052147/sbt-launch.jar
    "set logLevel in Global := Level.Debug"
    "iflast shell"
    about

    Getting net.java.dev.jna jna 3.2.3 ...
    :: retrieving :: org.scala-tools.sbt#boot-app
    	confs: [default]
    	1 artifacts copied, 0 already retrieved (838kB/15ms)
    Getting Scala 2.9.1.RC4 (for sbt)...
    :: retrieving :: org.scala-tools.sbt#boot-scala
    	confs: [default]
    	4 artifacts copied, 0 already retrieved (19939kB/97ms)
    Getting org.scala-tools.sbt sbt_2.9.1.RC4 0.11.0-20110825-052147 ...
    :: retrieving :: org.scala-tools.sbt#boot-app
    	confs: [default]
    	38 artifacts copied, 0 already retrieved (6948kB/71ms)
    [info] Set current project to default-06d8dd (in build file:/private/tmp/sbt-project/)
    [info] Reapplying settings...
    [info] Set current project to default-06d8dd (in build file:/private/tmp/sbt-project/)
    [info] This is sbt 0.11.0-20110825-052147
    [info] The current project is {file:/private/tmp/sbt-project/}default-06d8dd
    [info] The current project is built against Scala 2.9.1.RC4
    [info] sbt, sbt plugins, and build definitions are using Scala 2.9.1.RC4
    [info] All logging output for this session is available at /var/folders/iO/iOpjflOpHzG8Mr1BL67D6k+++TI/-Tmp-/sbt75419762724938334.log


Current -help output:

    % ./sbt -help

    Usage: sbt [options]

      -help           prints this message
      -v | -verbose   this runner is chattier
      -debug          set sbt log level to debug
      -nocolors       disable ANSI color codes
      -create         start sbt even in a directory with no project
      -sbtjar <path>  location of sbt launcher (default: ./.lib/<sbt version>/sbt-launch.jar)
      -sbtdir <path>  location of global settings and plugins (default: ~/.sbt)
         -ivy <path>  local Ivy repository (default: ~/.ivy2)
      -shared <path>  shared sbt boot directory (default: none, no sharing)

      # setting scala and sbt versions
      -28           set scala version to 2.8.2.RC1
      -29           set scala version to 2.9.1.RC4
      -210          set scala version to 2.10.0-SNAPSHOT
      -local <path> set scala version to local installation at path
      -snapshot     use a snapshot of sbt (otherwise, latest released version)

      # jvm options and output control
      JAVA_OPTS     environment variable, if unset uses "-Dfile.encoding=UTF8"
      SBT_OPTS      environment variable, if unset uses "-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xmx2g -Xss2m"
      .sbtopts      file in sbt root directory, if present contents passed to sbt
      -Dkey=val     pass -Dkey=val directly to the jvm
      -J-X          pass option -X directly to the jvm (-J is stripped)

    In the case of duplicated or conflicting options, the order above
    shows precedence: JAVA_OPTS lowest, command line options highest.
