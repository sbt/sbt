/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class NewFormatSpec extends AbstractSpec {
  implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  "New Format " should {
    "Handle lines " in {
      val rootPath = getClass.getClassLoader.getResource("").getPath + "/new-format/"
      println(s"Reading files from: $rootPath")
      val allFiles = new File(rootPath).listFiles.toList
      foreach(allFiles) { path =>
        println(s"$path")
        val lines = Source.fromFile(path).getLines().toList
        val (_, statements) = splitter(path, lines)
        statements.nonEmpty must be_==(true).setMessage(s"""
                               |***should contains statements***
                               |$lines """.stripMargin)
      }
    }
  }

}
