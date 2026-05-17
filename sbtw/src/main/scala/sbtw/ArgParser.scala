package sbtw

import scopt.OParser

object ArgParser:

  def parse(args: Array[String]): Option[LauncherOptions] =
    val b = OParser.builder[LauncherOptions]
    val parser =
      import b.*
      OParser.sequence(
        programName("sbtw"),
        head("sbtw", "Windows launcher for sbt"),
        opt[Unit]('h', "help").action((_, c) => c.copy(help = true)),
        opt[Unit]('v', "verbose").action((_, c) => c.copy(verbose = true)),
        opt[Unit]('d', "debug").action((_, c) => c.copy(debug = true)),
        opt[Unit]('V', "version").action((_, c) => c.copy(version = true)),
        opt[Unit]("numeric-version").action((_, c) => c.copy(numericVersion = true)),
        opt[Unit]("script-version").action((_, c) => c.copy(scriptVersion = true)),
        opt[Unit]("shutdownall").action((_, c) => c.copy(shutdownAll = true)),
        opt[Unit]("allow-empty").action((_, c) => c.copy(allowEmpty = true)),
        opt[Unit]("sbt-create").action((_, c) => c.copy(allowEmpty = true)),
        opt[Unit]("client").action((_, c) => c.copy(client = true)),
        opt[Unit]("server").action((_, c) => c.copy(server = true)),
        opt[Unit]("jvm-client").action((_, c) => c.copy(jvmClient = true)),
        opt[Unit]("no-server").action((_, c) => c.copy(noServer = true)),
        opt[Unit]("no-colors").action((_, c) => c.copy(noColors = true)),
        opt[Unit]("no-global").action((_, c) => c.copy(noGlobal = true)),
        opt[Unit]("no-share").action((_, c) => c.copy(noShare = true)),
        opt[Unit]("no-hide-jdk-warnings").action((_, c) => c.copy(noHideJdkWarnings = true)),
        opt[Unit]("debug-inc").action((_, c) => c.copy(debugInc = true)),
        opt[Unit]("timings").action((_, c) => c.copy(timings = true)),
        opt[Unit]("traces").action((_, c) => c.copy(traces = true)),
        opt[Unit]("batch").action((_, c) => c.copy(batch = true)),
        opt[String]("sbt-dir").action((x, c) => c.copy(sbtDir = Some(x))),
        opt[String]("sbt-boot").action((x, c) => c.copy(sbtBoot = Some(x))),
        opt[String]("sbt-cache").action((x, c) => c.copy(sbtCache = Some(x))),
        opt[String]("sbt-jar").action((x, c) => c.copy(sbtJar = Some(x))),
        opt[String]("sbt-version").action((x, c) => c.copy(sbtVersion = Some(x))),
        opt[String]("ivy").action((x, c) => c.copy(ivy = Some(x))),
        opt[Int]("mem").action((x, c) => c.copy(mem = Some(x))),
        opt[String]("supershell").action((x, c) => c.copy(supershell = Some(x))),
        opt[String]("color").action((x, c) => c.copy(color = Some(x))),
        opt[String]("autostart").action((x, c) => c.copy(autostart = Some(x))),
        opt[Int]("jvm-debug").action((x, c) => c.copy(jvmDebug = Some(x))),
        opt[String]("java-home").action((x, c) => c.copy(javaHome = Some(x))),
        opt[String]("experimental_execution_log").action((x, c) =>
          c.copy(experimentalExecutionLog = Some(x))
        ),
        arg[String]("<arg>")
          .unbounded()
          .optional()
          .action((x, c) => c.copy(residual = c.residual :+ x)),
      )
    OParser
      .parse(parser, args, LauncherOptions())
      .map: opts =>
        val sbtNew = opts.residual.contains("new") || opts.residual.contains("init")
        val isScript = opts.residual.exists(_.startsWith("-Dsbt.main.class=sbt.ScriptMain"))
        opts.copy(sbtNew = sbtNew, allowEmpty = opts.allowEmpty || isScript)
end ArgParser
