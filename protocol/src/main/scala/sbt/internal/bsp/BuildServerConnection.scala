/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

import sbt.internal.bsp.codec.JsonProtocol.BspConnectionDetailsFormat
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.File
import java.nio.file.{ Files, Paths }
import scala.util.Properties

object BuildServerConnection {
  final val name = "sbt"
  final val bspVersion = "2.1.0-M1"
  final val languages = Vector("scala")

  private final val SbtLaunchJar = "sbt-launch(-.*)?\\.jar".r

  private[sbt] def writeConnectionFile(sbtVersion: String, baseDir: File): Unit = {
    val bspConnectionFile = new File(baseDir, ".bsp/sbt.json")
    val javaHome = System.getProperty("java.home")
    val classPath = System.getProperty("java.class.path")

    val sbtScript = Option(System.getProperty("sbt.script"))
      .orElse(sbtScriptInPath)
      .map(script => s"-Dsbt.script=$script")

    // IntelliJ can start sbt even if the sbt script is not accessible from $PATH.
    // To do so it uses its own bundled sbt-launch.jar.
    // In that case, we must pass the path of the sbt-launch.jar to the BSP connection
    // so that the server can be started.
    // A known problem in that situation is that the .sbtopts and .jvmopts are not loaded.
    val sbtLaunchJar = classPath
      .split(File.pathSeparator)
      .find(jar => SbtLaunchJar.findFirstIn(jar).nonEmpty)
      .map(_.replace(" ", "%20"))
      .map(jar => s"--sbt-launch-jar=$jar")

    val argv =
      Vector(
        s"$javaHome/bin/java",
        "-Xms100m",
        "-Xmx100m",
        "-classpath",
        classPath,
      ) ++
        sbtScript ++
        Vector("xsbt.boot.Boot", "-bsp") ++
        (if (sbtScript.isEmpty) sbtLaunchJar else None)

    val details = BspConnectionDetails(name, sbtVersion, bspVersion, languages, argv)
    val json = Converter.toJson(details).get
    IO.write(bspConnectionFile, CompactPrinter(json), append = false)
  }

  private def sbtScriptInPath: Option[String] = {
    // For those who use an old sbt script, the -Dsbt.script is not set
    // As a fallback we try to find the sbt script in $PATH
    val fileName = if (Properties.isWin) "sbt.bat" else "sbt"
    val envPath = sys.env.collectFirst {
      case (k, v) if k.toUpperCase() == "PATH" => v
    }
    val allPaths = envPath.map(_.split(File.pathSeparator).map(Paths.get(_))).getOrElse(Array.empty)
    allPaths
      .map(_.resolve(fileName))
      .find(file => Files.exists(file) && Files.isExecutable(file))
      .map(_.toString.replace(" ", "%20"))
  }
}
