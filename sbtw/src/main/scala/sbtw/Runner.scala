package sbtw

import java.io.File
import scala.sys.process.*

object Runner {

  def findJavaCmd(javaHome: Option[String]): String = {
    val cmd = javaHome match {
      case Some(h) =>
        val exe = new File(h, "bin/java.exe")
        if (exe.isFile) exe.getAbsolutePath
        else
          sys.env
            .get("JAVACMD")
            .orElse(
              sys.env.get("JAVA_HOME").map(h0 => new File(h0, "bin/java.exe").getAbsolutePath)
            )
            .getOrElse("java")
      case None =>
        sys.env
          .get("JAVACMD")
          .orElse(sys.env.get("JAVA_HOME").map(h => new File(h, "bin/java.exe").getAbsolutePath))
          .getOrElse("java")
    }
    cmd.replace("\"", "")
  }

  def javaVersion(javaCmd: String): Int = {
    try {
      val pb = Process(Seq(javaCmd, "-Xms32M", "-Xmx32M", "-version"))
      val out = pb.!!
      val line = out.linesIterator.find(_.contains("version")).getOrElse("")
      val quoted = line.split("\"").lift(1).getOrElse("")
      val parts = quoted.replaceFirst("^1\\.", "").split("[.-_]")
      val major = parts.headOption.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
      if (quoted.startsWith("1.") && parts.nonEmpty)
        scala.util.Try(parts(0).toInt).toOption.getOrElse(major)
      else major
    } catch { case _: Exception => 0 }
  }

  def buildSbtOpts(opts: LauncherOptions): Seq[String] = {
    var s: Seq[String] = Nil
    if (opts.debug) s = s :+ "-debug"
    if (opts.debugInc) s = s :+ "-Dxsbt.inc.debug=true"
    if (opts.noColors) s = s :+ "-Dsbt.log.noformat=true"
    if (opts.noGlobal) s = s :+ "-Dsbt.global.base=project/.sbtboot"
    if (opts.noShare)
      s = s ++ Seq(
        "-Dsbt.global.base=project/.sbtboot",
        "-Dsbt.boot.directory=project/.boot",
        "-Dsbt.ivy.home=project/.ivy"
      )
    opts.supershell.foreach(v => s = s :+ s"-Dsbt.supershell=$v")
    opts.sbtVersion.foreach(v => s = s :+ s"-Dsbt.version=$v")
    opts.sbtDir.foreach(v => s = s :+ s"-Dsbt.global.base=$v")
    opts.sbtBoot.foreach(v => s = s :+ s"-Dsbt.boot.directory=$v")
    opts.sbtCache.foreach(v => s = s :+ s"-Dsbt.global.localcache=$v")
    opts.ivy.foreach(v => s = s :+ s"-Dsbt.ivy.home=$v")
    opts.color.foreach(v => s = s :+ s"-Dsbt.color=$v")
    if (opts.timings) s = s ++ Seq("-Dsbt.task.timings=true", "-Dsbt.task.timings.on.shutdown=true")
    if (opts.traces) s = s :+ "-Dsbt.traces=true"
    if (opts.noServer) s = s ++ Seq("-Dsbt.io.virtual=false", "-Dsbt.server.autostart=false")
    if (opts.jvmClient) s = s :+ "--client"
    s
  }

  def runNativeClient(sbtBinDir: File, scriptPath: String, opts: LauncherOptions): Int = {
    val sbtn = new File(sbtBinDir, "sbtn-x86_64-pc-win32.exe")
    if (!sbtn.isFile) {
      System.err.println("[error] sbtn-x86_64-pc-win32.exe not found in " + sbtBinDir)
      return 1
    }
    val args = Seq("--sbt-script=" + scriptPath.replace(" ", "%20")) ++
      (if (opts.verbose) Seq("-v") else Nil) ++
      opts.residual
    val cmd = sbtn.getAbsolutePath +: args
    if (opts.verbose) {
      System.err.println("# running native client")
      cmd.foreach(a => System.err.println(a))
    }
    val proc = Process(cmd, None, "SBT_SCRIPT" -> scriptPath)
    proc.!
  }

  def runJvm(
      javaCmd: String,
      javaOpts: Seq[String],
      sbtOpts: Seq[String],
      sbtJar: String,
      bootArgs: Seq[String],
      verbose: Boolean
  ): Int = {
    val toolOpts =
      sys.env.get("JAVA_TOOL_OPTIONS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val jdkOpts = sys.env.get("JDK_JAVA_OPTIONS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val fullJavaOpts = javaOpts ++ sbtOpts ++ toolOpts ++ jdkOpts
    val cmd = Seq(javaCmd) ++ fullJavaOpts ++ Seq("-cp", sbtJar, "xsbt.boot.Boot") ++ bootArgs
    if (verbose) {
      System.err.println("# Executing command line:")
      cmd.foreach(a => System.err.println(if (a.contains(" ")) s""""$a"""" else a))
    }
    Process(cmd).!
  }

  def shutdownAll(javaCmd: String): Int = {
    try {
      val jpsOut = Process(Seq("jps", "-lv")).!!
      val pids = jpsOut.linesIterator
        .filter(_.contains("xsbt.boot.Boot"))
        .flatMap { line =>
          val pidStr = line.trim.takeWhile(_.isDigit)
          if (pidStr.nonEmpty) scala.util.Try(pidStr.toLong).toOption else None
        }
        .toList
      pids.foreach { pid =>
        try Process(Seq("taskkill", "/F", "/PID", pid.toString)).!
        catch { case _: Exception => }
      }
      System.err.println(s"shutdown ${pids.size} sbt processes")
      0
    } catch { case _: Exception => 1 }
  }

  def splitResidual(residual: Seq[String]): (Seq[String], Seq[String]) = {
    var javaOpts: Seq[String] = Nil
    var bootArgs: Seq[String] = Nil
    var i = 0
    while (i < residual.size) {
      val a = residual(i)
      if (a.startsWith("-J")) javaOpts = javaOpts :+ a.drop(2)
      else if (a.startsWith("-X")) javaOpts = javaOpts :+ a
      else if (a.startsWith("-D") && a.contains("=")) bootArgs = bootArgs :+ a
      else if (a.startsWith("-D") && i + 1 < residual.size) {
        bootArgs = bootArgs :+ s"$a=${residual(i + 1)}"
        i += 1
      } else if (a.startsWith("-XX") && a.contains("=")) bootArgs = bootArgs :+ a
      else if (a.startsWith("-XX") && i + 1 < residual.size) {
        bootArgs = bootArgs :+ s"$a=${residual(i + 1)}"
        i += 1
      } else bootArgs = bootArgs :+ a
      i += 1
    }
    (javaOpts, bootArgs)
  }
}
