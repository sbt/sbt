/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.{ File, FilenameFilter }

import org.specs2.matcher.MatchResult

import scala.collection.GenTraversableOnce
import scala.io.Source
import SessionSettings.SessionSetting

abstract class AbstractSessionSettingsSpec(folder: String) extends AbstractSpec {
  protected val rootPath = getClass.getClassLoader.getResource("").getPath + folder
  println(s"Reading files from: $rootPath")
  protected val rootDir = new File(rootPath)

  "SessionSettings " should {
    "Be identical for empty map " in {
      def unit(f: File) = Seq((Source.fromFile(f).getLines().toList, Seq()))
      runTestOnFiles(unit)
    }

    "Replace statements " in {
      runTestOnFiles(replace)
    }
  }

  private def runTestOnFiles(expectedResultAndMap: File => Seq[(List[String], Seq[SessionSetting])])
    : MatchResult[GenTraversableOnce[File]] = {

    val allFiles = rootDir
      .listFiles(new FilenameFilter() {
        def accept(dir: File, name: String) = name.endsWith(".sbt.txt")
      })
      .toList
    foreach(allFiles) { file =>
      val originalLines = Source.fromFile(file).getLines().toList
      foreach(expectedResultAndMap(file)) {
        case (expectedResultList, commands) =>
          val resultList = SbtRefactorings.applySessionSettings((file, originalLines), commands)
          val expected = SbtParser(file, expectedResultList)
          val result = SbtParser(file, resultList._2)
          result.settings must_== expected.settings

      }
    }
  }

  protected def replace(f: File) = {
    val dirs = rootDir
      .listFiles(new FilenameFilter() {
        def accept(dir: File, name: String) = {
          val startsWith = f.getName + "_"
          name.startsWith(startsWith)
        }
      })
      .toSeq
    dirs.flatMap { dir =>
      val files = dir.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String) = name.endsWith(".set")
      })
      files.map { file =>
        val seq = Source.fromFile(file).getLines().toSeq
        val result = Source.fromFile(file.getAbsolutePath + ".result").getLines().toList
        val sessionSettings = seq.map(line => (null, Seq(line)))
        (result, sessionSettings)
      }
    }
  }

}

class SessionSettingsSpec extends AbstractSessionSettingsSpec("session-settings")

class SessionSettingsQuickSpec extends AbstractSessionSettingsSpec("session-settings-quick")
