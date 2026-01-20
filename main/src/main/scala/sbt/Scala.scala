/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.net.{ HttpURLConnection, URL }
import scala.util.control.NonFatal
import scala.util.Using
import scala.xml.XML

object Scala:
  private val ScalaNightliesBaseUrl = "https://repo.scala-lang.org/artifactory/maven-nightlies"
  private val MavenMetadataUrl =
    s"$ScalaNightliesBaseUrl/org/scala-lang/scala3-library_3/maven-metadata.xml"
  private val RCPattern = """3\.\d+\.\d+-RC\d+""".r
  private val NightlyPattern = """3\.\d+\.\d+.*-NIGHTLY""".r

  /**
   * Returns the latest Scala 3 Release Candidate version available from the Scala nightlies repository.
   *
   * This method fetches version information from repo.scala-lang.org and returns the latest
   * RC version matching the pattern `3.x.x-RC*`.
   *
   * @example
   * {{{
   * ThisBuild / scalaVersion := Scala.latestRC
   * ThisBuild / resolvers += Resolver.scalaNightlyRepository
   * }}}
   *
   * @throws RuntimeException if the latest RC version cannot be fetched
   * @return the latest Scala 3 RC version string (e.g., "3.8.1-RC1")
   */
  def latestRC: String =
    fetchLatestVersion(RCPattern, "RC").getOrElse:
      sys.error(
        "Failed to fetch latest Scala 3 RC version. Please specify an explicit version or check your internet connection."
      )

  private def fetchLatestVersion(pattern: scala.util.matching.Regex, versionType: String): Option[String] =
    try
      val url = URL(MavenMetadataUrl)
      val connection = url.openConnection.asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setConnectTimeout(5000)
      connection.setReadTimeout(5000)

      if connection.getResponseCode == HttpURLConnection.HTTP_OK then
        Using.resource(connection.getInputStream): stream =>
          val xml = XML.load(stream)
          parseVersionsFromXml(xml, pattern)
      else None
    catch
      case NonFatal(_) => None

  private def parseVersionsFromXml(xml: scala.xml.Elem, pattern: scala.util.matching.Regex): Option[String] =
    val versions = (xml \ "versioning" \ "versions" \ "version")
      .map(_.text)
      .filter(pattern.matches)
      .toSeq
    if versions.nonEmpty then
      val sorted = versions.sortWith(compareVersions)
      Some(sorted.last)
    else None

  private def compareVersions(v1: String, v2: String): Boolean =
    val parts1 = parseVersionParts(v1)
    val parts2 = parseVersionParts(v2)
    compareVersionParts(parts1, parts2) < 0

  private def parseVersionParts(version: String): (Int, Int, Int, Int) =
    val rcPattern = """3\.(\d+)\.(\d+)-RC(\d+)""".r
    val nightlyPattern = """3\.(\d+)\.(\d+).*-NIGHTLY""".r
    version match
      case rcPattern(major, minor, rc) =>
        (major.toInt, minor.toInt, rc.toInt, 0)
      case nightlyPattern(major, minor) =>
        (major.toInt, minor.toInt, Int.MaxValue, 0)
      case _ => (0, 0, 0, 0)

  private def compareVersionParts(
      v1: (Int, Int, Int, Int),
      v2: (Int, Int, Int, Int)
  ): Int =
    if v1._1 != v2._1 then v1._1.compareTo(v2._1)
    else if v1._2 != v2._2 then v1._2.compareTo(v2._2)
    else if v1._3 != v2._3 then v1._3.compareTo(v2._3)
    else v1._4.compareTo(v2._4)
end Scala
