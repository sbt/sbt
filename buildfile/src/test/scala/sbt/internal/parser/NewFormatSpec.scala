/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File

import scala.io.Source
import sbt.internal.inc.PlainVirtualFileConverter

object NewFormatSpec extends AbstractSpec {
  val converter = PlainVirtualFileConverter.converter
  given splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  test("New Format should handle lines") {
    val rootPath = getClass.getResource("/new-format").getPath
    println(s"Reading files from: $rootPath")
    val allFiles = new File(rootPath).listFiles.toList
    allFiles foreach { path =>
      println(s"$path")
      val vf = converter.toVirtualFile(path.toPath())
      val lines = Source.fromFile(path).getLines().toList
      val (_, statements) = splitter(vf, lines)
      assert(
        statements.nonEmpty,
        s"""
           |***should contains statements***
           |$lines """.stripMargin
      )
    }
  }
}
