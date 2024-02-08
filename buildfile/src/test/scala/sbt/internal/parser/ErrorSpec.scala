/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.util.MessageOnlyException
import scala.io.Source
import sbt.internal.inc.PlainVirtualFileConverter

import java.nio.file.Files
import java.nio.file.Paths
import verify.sourcecode.SourceLocation

object ErrorSpec extends AbstractSpec {

  val converter = PlainVirtualFileConverter.converter

  test("errors should contain file name and line number") {
    val rootPath = Paths.get(getClass.getResource("/error-format/").toURI)
    println(s"Reading files from: $rootPath")

    Files.list(rootPath).forEach { file =>
      print(s"Processing ${file.getFileName}: ")

      val vf = converter.toVirtualFile(file)
      val buildSbt = Source.fromFile(file.toUri).getLines.toSeq
      val message = interceptMessageException(SbtParser(vf, buildSbt))
      println(message)
      assert(message.contains(file.getFileName.toString))
      containsLineNumber(message)
    }
  }

  test("xml error") {
    val buildSbt =
      """
          |val a = <a/><b/>
          |val s = '
        """.stripMargin
    val message =
      interceptMessageException(SbtParser(SbtParser.FAKE_FILE, buildSbt.linesIterator.toSeq))
    println(message)
    assert(message.contains(SbtParser.FAKE_FILE.id))
  }

  private def containsLineNumber(message: String) =
    """\d+""".r.findFirstIn(message).getOrElse(fail(s"Line number not found in $message"))

  private def interceptMessageException(callback: => Unit)(using
      pos: SourceLocation
  ): String =
    try
      callback
      throw new AssertionError(s"$pos: expected a MessageOnlyException to be thrown")
    catch case ex: MessageOnlyException => ex.getMessage
}
