/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.xml.XML
import verify.BasicTestSuite

object ScalaTest extends BasicTestSuite:

  test("parseVersionsFromXml should extract RC versions from Maven metadata") {
    val xmlContent = """<?xml version="1.0" encoding="UTF-8"?>
      <metadata>
        <groupId>org.scala-lang</groupId>
        <artifactId>scala3-library_3</artifactId>
        <versioning>
          <versions>
            <version>3.8.0</version>
            <version>3.8.0-RC1</version>
            <version>3.8.0-RC2</version>
            <version>3.8.1-RC1</version>
            <version>3.8.1</version>
          </versions>
        </versioning>
      </metadata>"""
    val xml = XML.loadString(xmlContent)
    val result = ScalaTestHelper.parseVersionsFromXml(xml)
    assert(result.contains("3.8.1-RC1"))
    val versions = result.toSeq
    assert(versions.contains("3.8.0-RC1"))
    assert(versions.contains("3.8.0-RC2"))
    assert(versions.contains("3.8.1-RC1"))
    assert(!versions.contains("3.8.0"))
    assert(!versions.contains("3.8.1"))
  }

  test("compareVersions should correctly order RC versions") {
    assert(ScalaTestHelper.compareVersions("3.8.0-RC1", "3.8.0-RC2"))
    assert(ScalaTestHelper.compareVersions("3.8.0-RC2", "3.8.1-RC1"))
    assert(ScalaTestHelper.compareVersions("3.7.4-RC1", "3.8.0-RC1"))
    assert(!ScalaTestHelper.compareVersions("3.8.1-RC1", "3.8.0-RC2"))
  }

  test("parseVersionParts should correctly parse RC version strings") {
    val (major, minor, rc, _) = ScalaTestHelper.parseVersionParts("3.8.1-RC2")
    assert(major == 3)
    assert(minor == 8)
    assert(rc == 2)
  }

  test("parseVersionParts should handle invalid version strings") {
    val (major, minor, rc, _) = ScalaTestHelper.parseVersionParts("invalid")
    assert(major == 0)
    assert(minor == 0)
    assert(rc == 0)
  }
end ScalaTest

object ScalaTestHelper:
  import scala.xml.Elem
  private val RCPattern = """3\.\d+\.\d+-RC\d+""".r

  def parseVersionsFromXml(xml: Elem): Option[String] =
    val versions = (xml \ "versioning" \ "versions" \ "version")
      .map(_.text)
      .filter(RCPattern.matches)
      .toSeq
    if versions.nonEmpty then
      val sorted = versions.sortWith(compareVersions)
      Some(sorted.last)
    else None

  def compareVersions(v1: String, v2: String): Boolean =
    val parts1 = parseVersionParts(v1)
    val parts2 = parseVersionParts(v2)
    compareVersionParts(parts1, parts2) < 0

  def parseVersionParts(version: String): (Int, Int, Int, Int) =
    val pattern = """3\.(\d+)\.(\d+)-RC(\d+)""".r
    version match
      case pattern(major, minor, rc) =>
        (major.toInt, minor.toInt, rc.toInt, 0)
      case _ => (0, 0, 0, 0)

  private def compareVersionParts(
      v1: (Int, Int, Int, Int),
      v2: (Int, Int, Int, Int)
  ): Int =
    if v1._1 != v2._1 then v1._1.compareTo(v2._1)
    else if v1._2 != v2._2 then v1._2.compareTo(v2._2)
    else if v1._3 != v2._3 then v1._3.compareTo(v2._3)
    else v1._4.compareTo(v2._4)
