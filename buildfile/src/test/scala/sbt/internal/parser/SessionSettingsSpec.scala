/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.inc.PlainVirtualFileConverter
import sbt.internal.parser.SbtRefactorings.SessionSetting

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

abstract class AbstractSessionSettingsSpec(folder: String) extends AbstractSpec {
  private val rootDir = Paths.get(getClass.getResource("/" + folder).toURI)
  println(s"Reading files from: $rootDir")
  private val converter = PlainVirtualFileConverter.converter

  testOnFiles("should be identical for empty map") { f =>
    Seq((readLines(f), Seq()))
  }

  testOnFiles("should replace statements") { f =>
    val dirs = listFiles(rootDir)(_.startsWith(s"${f.getFileName}_"))
    dirs.flatMap { dir =>
      val files = listFiles(dir)(_.endsWith(".set"))
      files.map { file =>
        val seq = readLines(file)
        val result = readLines(Paths.get(s"$file.result"))
        val sessionSettings = seq.map(line => (null, Seq(line)))
        (result, sessionSettings)
      }
    }
  }

  private def testOnFiles(name: String)(
      expectedResultAndMap: Path => Seq[(Seq[String], Seq[SessionSetting])]
  ): Unit = test(name) {

    val allFiles = listFiles(rootDir)(_.endsWith(".sbt.txt"))

    allFiles.foreach { file =>
      val originalLines = readLines(file)
      val virtualFile = converter.toVirtualFile(file)
      expectedResultAndMap(file).foreach { case (expectedResultList, commands) =>
        val resultList = SbtRefactorings.applySessionSettings(originalLines, commands)
        val expected = SbtParser(virtualFile, expectedResultList)
        val result = SbtParser(virtualFile, resultList)
        assert(result.settings == expected.settings)
      }
    }
  }

  private def listFiles(dir: Path)(filter: String => Boolean): Seq[Path] =
    Files.walk(dir).iterator.asScala.filter(f => filter(f.getFileName.toString)).toSeq

  private def readLines(file: Path): Seq[String] =
    Files.readAllLines(file).asScala.toList
}

object SessionSettingsSpec extends AbstractSessionSettingsSpec("session-settings")

object SessionSettingsQuickSpec extends AbstractSessionSettingsSpec("session-settings-quick")
