/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.nio.file.{ Path, Paths }
import java.util.Locale

import scala.util.control.NonFatal
import scala.concurrent.duration._
import sbt.internal.inc.HashUtil
import sbt.internal.util.{ Terminal => ITerminal, Util }
import sbt.internal.util.complete.SizeParser
import sbt.io.syntax._
import sbt.librarymanagement.ivy.{ Credentials, FileCredentials }
import sbt.nio.Keys._

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

  def double(name: String, default: Double): Double =
    sys.props.get(name) match {
      case Some(str) =>
        try {
          str.toDouble
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

  @deprecated("Resident compilation is no longer supported", "1.4.0")
  def residentLimit: Int = int("sbt.resident.limit", 0)

  /**
   * Indicates whether formatting has been disabled in environment variables.
   * 1. -Dsbt.log.noformat=true means no formatting.
   * 2. -Dsbt.color=always/auto/never/true/false
   * 3. -Dsbt.colour=always/auto/never/true/false
   * 4. -Dsbt.log.format=always/auto/never/true/false
   */
  lazy val color: Boolean = ITerminal.isColorEnabled

  def closeClassLoaders: Boolean = getOrFalse("sbt.classloader.close")

  def fileCacheSize: Long =
    SizeParser(System.getProperty("sbt.file.cache.size", "128M")).getOrElse(128L * 1024 * 1024)
  def dumbTerm: Boolean = sys.env.get("TERM").contains("dumb")
  def supershell: Boolean = booleanOpt("sbt.supershell").getOrElse(!dumbTerm && color)

  def supershellMaxTasks: Int = int("sbt.supershell.maxitems", 8)
  def supershellSleep: Long = long("sbt.supershell.sleep", 500.millis.toMillis)
  def supershellThreshold: FiniteDuration = long("sbt.supershell.threshold", 100L).millis
  def supershellBlankZone: Int = int("sbt.supershell.blankzone", 1)

  def defaultUseCoursier: Boolean = {
    val coursierOpt = booleanOpt("sbt.coursier")
    val ivyOpt = booleanOpt("sbt.ivy")
    val notIvyOpt = ivyOpt map { !_ }
    coursierOpt.orElse(notIvyOpt).getOrElse(true)
  }

  def banner: Boolean = getOrTrue("sbt.banner")

  def useLog4J: Boolean = getOrFalse("sbt.log.uselog4j")
  def turbo: Boolean = getOrFalse("sbt.turbo")
  def pipelining: Boolean = getOrFalse("sbt.pipelining")

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

  def gcMonitor: Boolean = getOrTrue("sbt.gc.monitor")
  def gcWindow: FiniteDuration = int("sbt.gc.monitor.window", 10).seconds
  def gcRatio: Double = double("sbt.gc.monitor.ratio", 0.5)

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

  def onChangedBuildSource: WatchBuildSourceOption = {
    val sysPropKey = "sbt.build.onchange"
    sys.props.getOrElse(sysPropKey, "warn") match {
      case "reload" => ReloadOnSourceChanges
      case "warn"   => WarnOnSourceChanges
      case "ignore" => IgnoreSourceChanges
      case unknown =>
        System.err.println(s"Unknown $sysPropKey: $unknown.\nUsing warn.")
        sbt.nio.Keys.WarnOnSourceChanges
    }
  }

  def serverUseJni = getOrFalse("sbt.ipcsocket.jni")

  private[this] def file(value: String): File = new File(value)
  private[this] def home: File = file(sys.props("user.home"))

  /** Operating system specific cache directory, similar to Coursier cache.
   */
  def globalLocalCache: File = {
    val appName = "sbt"
    def propCacheDir: Option[File] = sys.props.get("sbt.global.localcache").map(file)
    def propCacheDir2: Option[File] =
      sys.props.get(BuildPaths.GlobalBaseProperty) match {
        case Some(base) => Some(file(base) / "cache")
        case _          => None
      }
    def envCacheDir: Option[File] = sys.env.get("SBT_LOCAL_CACHE").map(file)
    def windowsCacheDir: Option[File] =
      sys.env.get("LOCALAPPDATA") match {
        case Some(app) if Util.isWindows => Some(file(app) / appName)
        case _                           => None
      }
    def macCacheDir: Option[File] =
      if (Util.isMac) Some(home / "Library" / "Caches" / appName)
      else None
    def linuxCache: File =
      sys.env.get("XDG_CACHE_HOME") match {
        case Some(cache) => file(cache) / appName
        case _           => home / ".cache" / appName
      }
    def baseCache: File =
      propCacheDir
        .orElse(propCacheDir2)
        .orElse(envCacheDir)
        .orElse(windowsCacheDir)
        .orElse(macCacheDir)
        .getOrElse(linuxCache)
    baseCache.getAbsoluteFile / "v1"
  }

  lazy val sbtCredentialsEnv: Option[Credentials] =
    sys.env.get("SBT_CREDENTIALS").map(raw => new FileCredentials(new File(raw)))

  private[sbt] def setSwovalTempDir(): Unit = {
    val _ = getOrUpdateSwovalTmpDir(
      runtimeDirectory.resolve("swoval").toString
    )
  }
  private[sbt] def setIpcSocketTempDir(): Unit = {
    val _ = getOrUpdateIpcSocketTmpDir(
      runtimeDirectory.resolve("ipcsocket").toString
    )
  }
  private[this] lazy val getOrUpdateSwovalTmpDir: String => String =
    getOrUpdateSysProp("swoval.tmpdir")(_)
  private[this] lazy val getOrUpdateIpcSocketTmpDir: String => String =
    getOrUpdateSysProp("sbt.ipcsocket.tmpdir")(_)
  private[this] def getOrUpdateSysProp(key: String)(value: String): String = {
    val newVal = sys.props.getOrElse(key, value)
    sys.props += (key -> newVal)
    newVal
  }

  /**
   * This returns a temporary directory that is friendly to macOS, Linux,
   * Windows, and Docker environment.
   * Mostly these directories will be used as throw-away location to extract
   * native files etc.
   * A deterministic hash is appended in the directory name as "/tmp/.sbt1234ABCD/"
   * to avoid collision between multiple users in a shared server environment.
   */
  private[this] def runtimeDirectory: Path = {
    val hashValue =
      java.lang.Long.toHexString(HashUtil.farmHash(home.toString.getBytes("UTF-8")))
    val halfhash = hashValue.take(8)
    Paths
      .get(sys.env.getOrElse("XDG_RUNTIME_DIR", sys.props("java.io.tmpdir")))
      .resolve(s".sbt$halfhash")
  }
}
