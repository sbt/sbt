/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.{ File, FilenameFilter }

import scala.io.Source
import SessionSettings.SessionSetting

abstract class AbstractSessionSettingsSpec(folder: String) extends AbstractSpec {
  protected val rootPath = getClass.getResource("/" + folder).getPath
  println(s"Reading files from: $rootPath")
  protected val rootDir = new File(rootPath)

  test("SessionSettings should be identical for empty map") {
    def unit(f: File) = Seq((Source.fromFile(f).getLines().toList, Seq()))
    runTestOnFiles(unit)
  }

  test("it should replace statements") {
    runTestOnFiles(replace)
  }

  private def runTestOnFiles(
      expectedResultAndMap: File => Seq[(List[String], Seq[SessionSetting])]
  ): Unit = {

    val allFiles = rootDir
      .listFiles(new FilenameFilter() {
        def accept(dir: File, name: String) = name.endsWith(".sbt.txt")
      })
      .toList
    allFiles foreach { file =>
      val originalLines = Source.fromFile(file).getLines().toList
      expectedResultAndMap(file) foreach {
        case (expectedResultList, commands) =>
          val resultList = SbtRefactorings.applySessionSettings((file, originalLines), commands)
          val expected = SbtParser(file, expectedResultList)
          val result = SbtParser(file, resultList._2)
          assert(result.settings == expected.settings)
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
