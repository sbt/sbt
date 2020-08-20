/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

import java.io.File

import sbt.internal.bsp
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

object BuildServerConnection {
  final val name = "sbt"
  final val bspVersion = "2.0.0-M5"
  final val languages = Vector("scala")

  private[sbt] def writeConnectionFile(sbtVersion: String, baseDir: File): Unit = {
    import bsp.codec.JsonProtocol._
    val bspConnectionFile = new File(baseDir, ".bsp/sbt.json")
    val javaHome = System.getProperty("java.home")
    val classPath = System.getProperty("java.class.path")
    val argv =
      Vector(
        s"$javaHome/bin/java",
        "-Xms100m",
        "-Xmx100m",
        "-classpath",
        classPath,
        "xsbt.boot.Boot",
        "-bsp"
      )
    val details = BspConnectionDetails(name, sbtVersion, bspVersion, languages, argv)
    val json = Converter.toJson(details).get
    IO.write(bspConnectionFile, CompactPrinter(json), append = false)
  }
}
