package sbtw

import java.io.File
import scala.sys.process.*

object Main:

  def main(args: Array[String]): Unit =
    val cwd = new File(sys.props("user.dir"))
    val sbtHome = new File(
      sys.env
        .get("SBT_HOME")
        .getOrElse(
          sys.env.get("SBT_BIN_DIR").map(d => new File(d).getParent).getOrElse(cwd.getAbsolutePath)
        )
    )
    val sbtBinDir = new File(sbtHome, "bin")

    val fileSbtOpts = ConfigLoader.loadSbtOpts(cwd, sbtHome)
    val fileArgs = fileSbtOpts.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val allArgs = fileArgs ++ args

    ArgParser.parse(allArgs.toArray) match
      case None       => System.exit(1)
      case Some(opts) =>
        val exitCode = run(cwd, sbtHome, sbtBinDir, opts)
        System.exit(if exitCode == 0 then 0 else 1)

  private def run(cwd: File, sbtHome: File, sbtBinDir: File, opts: LauncherOptions): Int =
    if opts.help then printUsage()
    else if opts.version || opts.numericVersion || opts.scriptVersion then
      handleVersionCommands(cwd, sbtHome, sbtBinDir, opts)
    else if opts.shutdownAll then
      val javaCmd = Runner.findJavaCmd(opts.javaHome)
      Runner.shutdownAll(javaCmd)
    else if !opts.allowEmpty && !opts.sbtNew && !ConfigLoader.isSbtProjectDir(cwd) then
      System.err.println(
        "[error] Neither build.sbt nor a 'project' directory in the current directory: " + cwd
      )
      System.err.println("[error] run 'sbt new', touch build.sbt, or run 'sbt --allow-empty'.")
      1
    else
      val buildPropsVersion = ConfigLoader.sbtVersionFromBuildProperties(cwd)

      val javaCmd = Runner.findJavaCmd(opts.javaHome)
      val javaVer = Runner.javaVersion(javaCmd)
      val minJdk = Runner.minimumJdkVersion(buildPropsVersion)
      if javaVer > 0 && javaVer < minJdk then
        if minJdk >= 17 then
          System.err.println(
            "[error] sbt 2.x requires JDK 17 or above, but you have JDK " + javaVer
          )
        else System.err.println("[error] sbt requires at least JDK 8+, you have " + javaVer)
        1
      else
        val bspMode = opts.residual.exists(a => a == "bsp" || a == "-bsp" || a == "--bsp")
        val clientOpt = opts.client || sys.env.get("SBT_NATIVE_CLIENT").contains("true")
        val useNativeClient =
          if bspMode then false
          else shouldRunNativeClient(opts.copy(client = clientOpt), buildPropsVersion)

        if useNativeClient then
          val scriptPath = sbtBinDir.getAbsolutePath.replace("\\", "/") + "/sbt.bat"
          Runner.runNativeClient(sbtBinDir, scriptPath, opts)
        else
          val sbtJar = opts.sbtJar
            .filter(p => new File(p).isFile)
            .getOrElse(new File(sbtBinDir, "sbt-launch.jar").getAbsolutePath)
          if !new File(sbtJar).isFile then
            System.err.println("[error] Launcher jar not found: " + sbtJar)
            1
          else
            var javaOpts = ConfigLoader.loadJvmOpts(cwd)
            if javaOpts.isEmpty then javaOpts = ConfigLoader.defaultJavaOpts
            var sbtOpts = Runner.buildSbtOpts(opts)

            val (residualJava, bootArgs) = Runner.splitResidual(opts.residual)
            javaOpts = javaOpts ++ residualJava

            val (finalJava, finalSbt) = if opts.mem.isDefined then
              val evictedJava = Memory.evictMemoryOpts(javaOpts)
              val evictedSbt = Memory.evictMemoryOpts(sbtOpts)
              val memOpts = Memory.addMemory(opts.mem.get, javaVer)
              (evictedJava ++ memOpts, evictedSbt)
            else Memory.addDefaultMemory(javaOpts, sbtOpts, javaVer, LauncherOptions.defaultMemMb)
            sbtOpts = finalSbt

            if !opts.noHideJdkWarnings && javaVer >= 25 then
              sbtOpts = sbtOpts ++ Seq(
                "--sun-misc-unsafe-memory-access=allow",
                "--enable-native-access=ALL-UNNAMED"
              )
            val javaOptsWithDebug = opts.jvmDebug.fold(finalJava)(port =>
              finalJava :+ s"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$port"
            )

            Runner.runJvm(javaCmd, javaOptsWithDebug, sbtOpts, sbtJar, bootArgs, opts.verbose)

  private def shouldRunNativeClient(
      opts: LauncherOptions,
      buildPropsVersion: Option[String]
  ): Boolean =
    if opts.sbtNew then false
    else if opts.jvmClient then false
    else
      val version = buildPropsVersion.getOrElse(LauncherOptions.initSbtVersion)
      val parts = version.split("[.-]").take(2).flatMap(s => scala.util.Try(s.toInt).toOption)
      val (major, minor) = (parts.lift(0).getOrElse(0), parts.lift(1).getOrElse(0))
      if major >= 2 then !opts.server
      else if major >= 1 && minor >= 4 then opts.client
      else false

  private def handleVersionCommands(
      cwd: File,
      sbtHome: File,
      sbtBinDir: File,
      opts: LauncherOptions
  ): Int =
    if opts.scriptVersion then
      println(LauncherOptions.initSbtVersion)
      0
    else if opts.version then
      if ConfigLoader.isSbtProjectDir(cwd) then
        projectSbtVersion(cwd).foreach: version =>
          println("sbt version in this project: " + version)
      println("sbt runner version: " + LauncherOptions.initSbtVersion)
      System.err.println("[info] sbt runner (sbtw) is a runner to run any declared version of sbt.")
      System.err.println(
        "[info] Actual version of sbt is declared using project\\build.properties for each build."
      )
      0
    else if opts.numericVersion then
      val javaCmd = Runner.findJavaCmd(opts.javaHome)
      val sbtJar = opts.sbtJar
        .filter(p => new File(p).isFile)
        .getOrElse(new File(sbtBinDir, "sbt-launch.jar").getAbsolutePath)
      if !new File(sbtJar).isFile then
        System.err.println("[error] Launcher jar not found for version check")
        1
      else
        try
          val out = Process(Seq(javaCmd, "-jar", sbtJar, "sbtVersion")).!!
          println(out.linesIterator.toSeq.lastOption.map(_.trim).getOrElse(""))
          0
        catch { case _: Exception => 1 }
    else 0

  private def projectSbtVersion(cwd: File): Option[String] =
    ConfigLoader.sbtVersionFromBuildProperties(cwd).flatMap(normalizeVersion)

  private def normalizeVersion(version: String): Option[String] =
    val trimmed = version.trim
    if trimmed.nonEmpty then Some(trimmed) else None

  private def printUsage(): Int =
    println("""
      |Usage: sbtw [options]
      |
      |  -h | --help         print this message
      |  -v | --verbose      this runner is chattier
      |  -V | --version      print sbt version information
      |  --numeric-version   print the numeric sbt version
      |  --script-version    print the version of sbt script
      |  shutdownall         shutdown all running sbt processes
      |  -d | --debug        set sbt log level to debug
      |  --allow-empty       start sbt even if current directory contains no sbt project
      |  --client            run sbtn (native client), and start sbt server in the background
      |  --server            run sbt server in the foreground, instead of using sbtn
      |  --jvm-client        run JVM client, and start sbt server in the background
      |  --mem <integer>     set memory options (default: 1024)
      |  --sbt-version <v>   use the specified version of sbt
      |  --sbt-jar <path>    use the specified jar as the sbt launcher
      |  --java-home <path>  alternate JAVA_HOME
      |  -Dkey=val           pass -Dkey=val to the JVM
      |  -X<flag>            pass -X<flag> to the JVM (e.g. -Xmx1G)
      |  -J-X                pass -X to the JVM (-J is stripped)
      |""".stripMargin)
    0
end Main
