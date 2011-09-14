sbt: the rebel cut
==================

An alternative script for running [sbt](https://github.com/harrah/xsbt).
It works with sbt 0.7x projects as well as 0.10+.  If you're in an sbt
project directory, the runner will figure out the versions of sbt
and scala required by the project and download them if necessary.

There's also a template project sbt coming together, but it's unfinished.
See below for how to pull the template project in as a plugin.
However the runner is quite useful already.

Here's a sample use of the runner: it creates a new project using a
snapshot version of sbt as well as a snapshot version of scala, then
runs the sbt "about" command.

    % sbt -d -sbt-snapshot -210 -sbt-create about
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
    "++ 2.10.0-SNAPSHOT"
    "iflast shell"
    about

    Getting net.java.dev.jna jna 3.2.3 ...
    :: retrieving :: org.scala-tools.sbt#boot-app
    	confs: [default]
    	1 artifacts copied, 0 already retrieved (838kB/12ms)
    Getting Scala 2.9.1.RC4 (for sbt)...
    :: retrieving :: org.scala-tools.sbt#boot-scala
    	confs: [default]
    	4 artifacts copied, 0 already retrieved (19939kB/32ms)
    Getting org.scala-tools.sbt sbt_2.9.1.RC4 0.11.0-20110825-052147 ...
    :: retrieving :: org.scala-tools.sbt#boot-app
    	confs: [default]
    	38 artifacts copied, 0 already retrieved (6948kB/45ms)
    [info] Set current project to default-30ae78 (in build file:/private/var/folders/1w/zm_1vksn3y7gn127bkwt841w0000gn/T/abc.h9FdreF1/)
    [info] Reapplying settings...
    [info] Set current project to default-30ae78 (in build file:/private/var/folders/1w/zm_1vksn3y7gn127bkwt841w0000gn/T/abc.h9FdreF1/)
    Setting version to 2.10.0-SNAPSHOT
    [info] Set current project to default-30ae78 (in build file:/private/var/folders/1w/zm_1vksn3y7gn127bkwt841w0000gn/T/abc.h9FdreF1/)
    Getting Scala 2.10.0-SNAPSHOT ...
    :: retrieving :: org.scala-tools.sbt#boot-scala
    	confs: [default]
    	4 artifacts copied, 0 already retrieved (20650kB/58ms)
    [info] This is sbt 0.11.0-20110825-052147
    [info] The current project is {file:/private/var/folders/1w/zm_1vksn3y7gn127bkwt841w0000gn/T/abc.h9FdreF1/}default-30ae78
    [info] The current project is built against Scala 2.10.0-SNAPSHOT
    [info] sbt, sbt plugins, and build definitions are using Scala 2.9.1.RC4
    [info] All logging output for this session is available at /var/folders/1w/zm_1vksn3y7gn127bkwt841w0000gn/T/sbt8625229869994477318.log


Current -help output:

    Usage: sbt [options]

      -h | -help        print this message
      -v | -verbose     this runner is chattier
      -d | -debug       set sbt log level to debug
      -no-colors        disable ANSI color codes
      -sbt-create       start sbt even if current directory contains no sbt project
      -sbt-dir  <path>  path to global settings/plugins directory (default: ~/.sbt)
      -sbt-boot <path>  path to shared boot directory (default: ~/.sbt/boot in 0.11 series)
      -ivy      <path>  path to local Ivy repository (default: ~/.ivy2)

      # sbt version (default: from project/build.properties if present, else latest release)
      -sbt-version  <version>   use the specified version of sbt
      -sbt-jar      <path>      use the specified jar as the sbt launcher
      -sbt-rc                   use an RC version of sbt
      -sbt-snapshot             use a snapshot version of sbt

      # scala version (default: latest release)
      -28                       use 2.8.1
      -29                       use 2.9.1
      -210                      use 2.10.0-SNAPSHOT
      -scala-home <path>        use the scala build at the specified directory
      -scala-version <version>  use the specified version of scala

      # java version (default: java from PATH, currently java version "1.6.0_26")
      -java-home <path>         alternate JAVA_HOME

      # jvm options and output control
      JAVA_OPTS     environment variable, if unset uses "-Dfile.encoding=UTF8"
      SBT_OPTS      environment variable, if unset uses "-XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m -Xms1536m -Xmx1536m -Xss2m"
      .sbtopts      if this file exists in the sbt root, it is prepended to the runner args
      -Dkey=val     pass -Dkey=val directly to the java runtime
      -J-X          pass option -X directly to the java runtime (-J is stripped)

    In the case of duplicated or conflicting options, the order above
    shows precedence: JAVA_OPTS lowest, command line options highest.


## SBT Extra plugin

To see the plugin in action, including the thrilling custom sbt command "help-names":

    cd template-project && ../sbt -sbt-rc help-names
    
The template files are:

    project/plugins/project/Build.scala   # you can use this as-is if you want
    project/Build.scala                   # this is a starting point for your real Build.scala

The Template build isn't quite finished.  There will most likely be a build.sbt DSL variant that does not require a project scala file.

## Simple task DSL

SBT extras defines a simplified task DSL for those who are defining simple tasks that do not need to be relied upon, or you are unsure and can refactor later.   Once including the sbt-extra-plugin, all you have to do is place the following in your build.sbt to create tasks:

    simple_task("zomg") is { println("ZOMG") }

or if you need to depend on other keys:

    simple_task("zomg2") on (name, version) is { (n,v) => println("ZOMG " + n + " = " + v + " !!!!!") }

The DSL currently supports between 2 and 9 dependencies.  The DSL does not allow defining tasks on different configurations, although this will be added shortly.
