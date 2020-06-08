/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

object BuildServerConnection {
  final val name = "sbt"
  final val bspVersion = "2.0.0-M5"
  final val languages = Vector("scala")

  def details(sbtVersion: String, launcherClassPath: String): BspConnectionDetails = {
    val argv =
      Vector(
        "java",
        "-Xms100m",
        "-Xmx100m",
        "-classpath",
        launcherClassPath,
        "xsbt.boot.Boot",
        "-bsp"
      )
    BspConnectionDetails(name, sbtVersion, bspVersion, languages, argv)
  }
}
