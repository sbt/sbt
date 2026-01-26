/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URLClassLoader
import java.nio.file.{ Path, Paths }
import java.lang.{ ProcessBuilder as JProcessBuilder }
import sbt.internal.worker.ConsoleConfig
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }

/**
 * Utilities for running the Scala console in a forked JVM.
 */
private[sbt] object ForkConsole:
  /**
   * Run the Scala console in a forked JVM.
   *
   * @param config Configuration for the console
   * @param forkOptions Fork options (javaHome, jvmOptions, etc.)
   * @return Exit code of the forked process
   */
  def apply(config: ConsoleConfig, forkOptions: ForkOptions): Int =
    IO.withTemporaryDirectory: tempDir =>
      import sbt.internal.worker.codec.JsonProtocol.given
      val json = Converter.toJson[ConsoleConfig](config).get
      val params = tempDir.toPath.resolve("console-params.json")
      IO.write(params.toFile, CompactPrinter(json))
      run(
        mainClass = classOf[ConsoleMain].getCanonicalName,
        classpath = currentClasspath,
        args = List(s"@$params"),
        forkOptions = forkOptions,
      )

  /**
   * Run an arbitrary main class in a forked JVM with full terminal inheritance.
   * This is critical for interactive console to work properly with JLine.
   */
  def run(
      mainClass: String,
      classpath: List[Path],
      args: List[String],
      forkOptions: ForkOptions,
  ): Int =
    val jlineJars = Seq(
      IO.classLocationPath(classOf[jline.Terminal]),
      IO.classLocationPath(classOf[org.jline.terminal.Terminal]),
      IO.classLocationPath(classOf[org.jline.reader.LineReader]),
      IO.classLocationPath(classOf[org.jline.utils.InfoCmp]),
      IO.classLocationPath(classOf[org.jline.keymap.KeyMap[?]]),
    ).distinct
    val fullCp = (classpath ++ jlineJars).distinct

    // Build environment variables for proper terminal handling
    val termEnv = sys.env.get("TERM").getOrElse("xterm-256color")
    val baseEnv = forkOptions.envVars ++ Map(
      "TERM" -> termEnv,
      "COLORTERM" -> sys.env.getOrElse("COLORTERM", "truecolor"),
    )

    // Add JLine-related JVM options to help with terminal detection
    val jlineJvmOpts = Seq(
      s"-Dorg.jline.terminal.type=$termEnv",
      "-Djline.terminal=auto",
    )
    val allJvmOpts = forkOptions.runJVMOptions ++ jlineJvmOpts

    // Build the java command
    val javaHome = forkOptions.javaHome.getOrElse(new File(System.getProperty("java.home")))
    val javaCmd = new File(new File(javaHome, "bin"), "java").getAbsolutePath

    // Build full command line
    val cmdArgs = Seq(javaCmd) ++
      allJvmOpts ++
      Seq("-classpath", fullCp.mkString(File.pathSeparator), mainClass) ++
      args

    // Use ProcessBuilder directly with inheritIO() for proper terminal handling
    // This is critical for JLine arrow keys to work - all streams must be inherited
    val jpb = new JProcessBuilder(cmdArgs*)
    jpb.inheritIO() // Inherit stdin, stdout, stderr from parent process
    forkOptions.workingDirectory.foreach(jpb.directory(_))

    // Set environment variables
    val env = jpb.environment()
    baseEnv.foreach { case (k, v) => env.put(k, v) }

    // Start and wait for process
    val process = jpb.start()
    process.waitFor()

  /**
   * Get the classpath of the current class loader.
   * This is used to pass the sbt classes to the forked JVM.
   */
  def currentClasspath: List[Path] =
    val cl = classOf[ForkConsole.type].getClassLoader match
      case cl: URLClassLoader => cl
      case other =>
        throw RuntimeException(
          s"Expected URLClassLoader but got ${other.getClass.getName}"
        )
    val urls = cl.getURLs.toList
    val extraJars = Vector(
      IO.classLocationPath(classOf[xsbti.compile.ScalaInstance]),
      IO.classLocationPath(classOf[xsbti.Logger]),
      IO.classLocationPath(classOf[sbt.internal.inc.AnalyzingCompiler]),
      IO.classLocationPath(classOf[sbt.internal.inc.classpath.ClasspathUtil.type]),
      IO.classLocationPath(classOf[sbt.util.Logger]),
      IO.classLocationPath(classOf[sjsonnew.JsonFormat[?]]),
    )
    (urls.map(u => Paths.get(u.toURI)) ++ extraJars).distinct
end ForkConsole
