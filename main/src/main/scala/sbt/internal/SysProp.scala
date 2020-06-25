/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.util.Locale

import scala.util.control.NonFatal
import sbt.internal.util.ConsoleAppender
import sbt.internal.util.complete.SizeParser

// See also BuildPaths.scala
// See also LineReader.scala
object SysProp {
  def booleanOpt(name: String): Option[Boolean] =
    sys.props.get(name) match {
      case Some(x) => parseBoolean(x)
      case _ =>
        sys.env.get(name.toUpperCase(Locale.ENGLISH).replace('.', '_')) match {
          case Some(x) => parseBoolean(x)
          case _       => None
        }
    }
  private def parseBoolean(value: String): Option[Boolean] =
    value.toLowerCase(Locale.ENGLISH) match {
      case "1" | "always" | "true" => Some(true)
      case "0" | "never" | "false" => Some(false)
      case "auto"                  => None
      case _                       => None
    }

  def getOrFalse(name: String): Boolean = booleanOpt(name).getOrElse(false)
  def getOrTrue(name: String): Boolean = booleanOpt(name).getOrElse(true)

  def long(name: String, default: Long): Long =
    sys.props.get(name) match {
      case Some(str) =>
        try {
          str.toLong
        } catch {
          case NonFatal(_) => default
        }
      case _ => default
    }

  def int(name: String, default: Int): Int =
    sys.props.get(name) match {
      case Some(str) =>
        try {
          str.toInt
        } catch {
          case NonFatal(_) => default
        }
      case _ => default
    }

  // System property style:
  //   1. use sbt. prefix
  //   2. prefer short nouns
  //   3. use dot for namespacing, and avoid making dot-separated English phrase
  //   4. make active/enable properties, instead of "sbt.disable."
  //
  // Good: sbt.offline
  //
  // Bad:
  // sbt.disable.interface.classloader.cache
  // sbt.task.timings.on.shutdown
  // sbt.skip.version.write -> sbt.genbuildprops=false

  def offline: Boolean = getOrFalse("sbt.offline")
  def traces: Boolean = getOrFalse("sbt.traces")
  def client: Boolean = getOrFalse("sbt.client")
  def ci: Boolean = getOrFalse("sbt.ci")
  def allowRootDir: Boolean = getOrFalse("sbt.rootdir")
  def legacyTestReport: Boolean = getOrFalse("sbt.testing.legacyreport")
  def semanticdb: Boolean = getOrFalse("sbt.semanticdb")
  def forceServerStart: Boolean = getOrFalse("sbt.server.forcestart")

  def watchMode: String =
    sys.props.get("sbt.watch.mode").getOrElse("auto")

  def residentLimit: Int = int("sbt.resident.limit", 0)

  /**
   * Indicates whether formatting has been disabled in environment variables.
   * 1. -Dsbt.log.noformat=true means no formatting.
   * 2. -Dsbt.color=always/auto/never/true/false
   * 3. -Dsbt.colour=always/auto/never/true/false
   * 4. -Dsbt.log.format=always/auto/never/true/false
   */
  lazy val color: Boolean = ConsoleAppender.formatEnabledInEnv

  def closeClassLoaders: Boolean = getOrFalse("sbt.classloader.close")

  def fileCacheSize: Long =
    SizeParser(System.getProperty("sbt.file.cache.size", "128M")).getOrElse(128L * 1024 * 1024)
  def dumbTerm: Boolean = sys.env.get("TERM").contains("dumb")
  def supershell: Boolean = booleanOpt("sbt.supershell").getOrElse(!dumbTerm && color)

  def supershellSleep: Long = long("sbt.supershell.sleep", 100L)
  def supershellBlankZone: Int = int("sbt.supershell.blankzone", 1)

  def defaultUseCoursier: Boolean = {
    val coursierOpt = booleanOpt("sbt.coursier")
    val ivyOpt = booleanOpt("sbt.ivy")
    val notIvyOpt = ivyOpt map { !_ }
    coursierOpt.orElse(notIvyOpt).getOrElse(true)
  }

  def banner: Boolean = getOrTrue("sbt.banner")

  def turbo: Boolean = getOrFalse("sbt.turbo")

  def taskTimings: Boolean = getOrFalse("sbt.task.timings")
  def taskTimingsOnShutdown: Boolean = getOrFalse("sbt.task.timings.on.shutdown")
  def taskTimingsThreshold: Long = long("sbt.task.timings.threshold", 0L)
  def taskTimingsOmitPaths: Boolean = getOrFalse("sbt.task.timings.omit.paths")
  def taskTimingsUnit: (String, Int) =
    System.getProperty("sbt.task.timings.unit", "ms") match {
      case "ns" => ("ns", 0)
      case "us" => ("µs", 3)
      case "ms" => ("ms", 6)
      case "s"  => ("sec", 9)
      case x =>
        System.err.println(s"Unknown sbt.task.timings.unit: $x.\nUsing milliseconds.")
        ("ms", 6)
    }

  /** Generate build.properties if missing. */
  def genBuildProps: Boolean =
    booleanOpt("sbt.genbuildprops") match {
      case Some(x) => x
      case None =>
        booleanOpt("sbt.skip.version.write") match {
          case Some(skip) => !skip
          case None       => true
        }
    }
}
