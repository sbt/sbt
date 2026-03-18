/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

import sbt.internal.bsp.codec.JsonProtocol.BspConnectionDetailsFormat
import sbt.internal.util.Util
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

    val sbtScript = Option(System.getProperty("sbt.script"))
      .map(_.replace("%20", " "))
      .filter(_.nonEmpty)
      .orElse(sbtScriptInPath)

    val argv = sbtScript match
      case Some(script) =>
        Vector(script, "bsp")
      case None =>
        buildFallbackArgv

    val details = BspConnectionDetails(name, sbtVersion, bspVersion, languages, argv)
    val json = Converter.toJson(details).get
    IO.write(bspConnectionFile, CompactPrinter(json), append = false)
  }

  private[sbt] def buildFallbackArgv: Vector[String] = {
    val javaHome = Util.javaHome
    val classPath = System.getProperty("java.class.path")
    val sbtOptsArgs = parseSbtOpts(sys.env.get("SBT_OPTS"))
    val sbtLaunchJar = classPath
      .split(File.pathSeparator)
      .find(jar => SbtLaunchJar.findFirstIn(jar).nonEmpty)
      .map(_.replace(" ", "%20"))
      .map(jar => s"--sbt-launch-jar=$jar")

    Vector(
      s"$javaHome/bin/java",
      "-Xms100m",
      "-Xmx100m",
    ) ++
      sbtOptsArgs ++
      Vector(
        "-classpath",
        classPath,
      ) ++
      Vector("xsbt.boot.Boot", "-bsp") ++
      sbtLaunchJar
  }

  private[sbt] def sbtScriptInPath: Option[String] = {
    val fileName = if (Properties.isWin) "sbt.bat" else "sbt"
    val envPath = sys.env.collectFirst {
      case (k, v) if k.toUpperCase() == "PATH" => v
    }
    val allPaths = envPath match
      case Some(path) => path.split(File.pathSeparator).toList.map(Paths.get(_))
      case _          => Nil
    allPaths
      .map(_.resolve(fileName))
      .find(file => Files.exists(file) && Files.isExecutable(file))
      .map(_.toString)
  }

  private[sbt] def parseSbtOpts(sbtOpts: Option[String]): Vector[String] =
    sbtOpts match
      case Some(opts) if opts.nonEmpty =>
        opts
          .split("\\s+")
          .filter(arg => arg.startsWith("-D") || arg.startsWith("-X") || arg.startsWith("-J"))
          .map(arg => if (arg.startsWith("-J")) arg.stripPrefix("-J") else arg)
          .toVector
      case _ => Vector.empty
}
