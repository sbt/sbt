package sbtw

import java.io.File
import java.lang.ProcessBuilder as JProcessBuilder
import scala.sys.process.*

object Runner:

  def findJavaCmd(javaHome: Option[String]): String =
    val cmd = javaHome match
      case Some(h) =>
        val exe = new File(h, "bin/java.exe")
        if exe.isFile then exe.getAbsolutePath
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
    cmd.replace("\"", "")

  def javaVersion(javaCmd: String): Int =
    try
      val pb = Process(Seq(javaCmd, "-Xms32M", "-Xmx32M", "-version"))
      val out = pb.!!
      val line = out.linesIterator.find(_.contains("version")).getOrElse("")
      val quoted = line.split("\"").lift(1).getOrElse("")
      val parts = quoted.replaceFirst("^1\\.", "").split("[.-_]")
      val major = parts.headOption.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
      if quoted.startsWith("1.") && parts.nonEmpty then
        scala.util.Try(parts(0).toInt).toOption.getOrElse(major)
      else major
    catch { case _: Exception => 0 }

  /** Returns the minimum JDK version required for the given sbt version. */
  def minimumJdkVersion(sbtVersion: Option[String]): Int =
    val isSbt2 = sbtVersion.exists(v => v.takeWhile(_.isDigit).toIntOption.exists(_ >= 2))
    if isSbt2 then 17 else 8

  def buildSbtOpts(opts: LauncherOptions): Seq[String] =
    var s: Seq[String] = Nil
    if opts.debug then s = s :+ "-debug"
    if opts.debugInc then s = s :+ "-Dxsbt.inc.debug=true"
    if opts.noColors then s = s :+ "-Dsbt.log.noformat=true"
    if opts.noGlobal then s = s :+ "-Dsbt.global.base=project/.sbtboot"
    if opts.noShare then
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
    opts.autostart.foreach(v => s = s :+ s"-Dsbt.server.autostart=$v")
    if opts.timings then
      s = s ++ Seq("-Dsbt.task.timings=true", "-Dsbt.task.timings.on.shutdown=true")
    if opts.traces then s = s :+ "-Dsbt.traces=true"
    if opts.noServer then s = s ++ Seq("-Dsbt.io.virtual=false", "-Dsbt.server.autostart=false")
    if opts.jvmClient then s = s :+ "--client"
    s

  def runNativeClient(sbtBinDir: File, scriptPath: String, opts: LauncherOptions): Int =
    val sbtn = new File(sbtBinDir, "sbtn-x86_64-pc-win32.exe")
    if !sbtn.isFile then
      System.err.println("[error] sbtn-x86_64-pc-win32.exe not found in " + sbtBinDir)
      return 1
    val args = Seq("--sbt-script=" + scriptPath.replace(" ", "%20")) ++
      (if opts.verbose then Seq("-v") else Nil) ++
      opts.residual
    val cmd = sbtn.getAbsolutePath +: args
    if opts.verbose then
      System.err.println("# running native client")
      cmd.foreach(a => System.err.println(a))
    val proc = Process(cmd, None, "SBT_SCRIPT" -> scriptPath)
    proc.!

  def runJvm(
      javaCmd: String,
      javaOpts: Seq[String],
      sbtOpts: Seq[String],
      sbtJar: String,
      bootArgs: Seq[String],
      verbose: Boolean
  ): Int =
    val toolOpts =
      sys.env.get("JAVA_TOOL_OPTIONS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val jdkOpts = sys.env.get("JDK_JAVA_OPTIONS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val fullJavaOpts = javaOpts ++ sbtOpts ++ toolOpts ++ jdkOpts
    val cmd = Seq(javaCmd) ++ fullJavaOpts ++ Seq("-cp", sbtJar, "xsbt.boot.Boot") ++ bootArgs
    if verbose then
      System.err.println("# Executing command line:")
      cmd.foreach(a => System.err.println(if a.contains(" ") then s""""$a"""" else a))
    val jpb = new JProcessBuilder(cmd*)
    jpb.inheritIO()
    val p = jpb.start()
    try
      p.waitFor()
      p.exitValue()
    finally if p.isAlive then p.destroy()

  def shutdownAll(javaCmd: String): Int =
    try
      val jpsOut = Process(Seq("jps", "-lv")).!!
      val pids = jpsOut.linesIterator
        .filter(_.contains("xsbt.boot.Boot"))
        .flatMap: line =>
          val pidStr = line.trim.takeWhile(_.isDigit)
          if pidStr.nonEmpty then scala.util.Try(pidStr.toLong).toOption else None
        .toList
      pids.foreach: pid =>
        try Process(Seq("taskkill", "/F", "/PID", pid.toString)).!
        catch { case _: Exception => }
      System.err.println(s"shutdown ${pids.size} sbt processes")
      0
    catch { case _: Exception => 1 }

  def splitResidual(residual: Seq[String]): (Seq[String], Seq[String]) =
    var javaOpts: Seq[String] = Nil
    var bootArgs: Seq[String] = Nil
    var i = 0
    while i < residual.size do
      val a = residual(i)
      if a.startsWith("-J") then javaOpts = javaOpts :+ a.drop(2)
      else if a.startsWith("-X") then javaOpts = javaOpts :+ a
      else if a.startsWith("-D") && a.contains("=") then bootArgs = bootArgs :+ a
      else if a.startsWith("-D") && i + 1 < residual.size then
        bootArgs = bootArgs :+ s"$a=${residual(i + 1)}"
        i += 1
      else if a.startsWith("-XX") && a.contains("=") then bootArgs = bootArgs :+ a
      else if a.startsWith("-XX") && i + 1 < residual.size then
        bootArgs = bootArgs :+ s"$a=${residual(i + 1)}"
        i += 1
      else bootArgs = bootArgs :+ a
      i += 1
    (javaOpts, bootArgs)
end Runner
