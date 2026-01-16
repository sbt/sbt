/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.shaded.scalajson.ast.unsafe.*
import sjsonnew.*, support.scalajson.unsafe.*
import verify.BasicTestSuite
import sbt.io.IO

object FileInfoSpec extends BasicTestSuite:
  val file = new java.io.File(".").getAbsoluteFile
  val fileInfo: ModifiedFileInfo = FileModified(file, IO.getModifiedTimeOrZero(file))
  val filesInfo = FilesInfo(Set(fileInfo))

  test("round trip"):
    assertRoundTrip(filesInfo)

  def assertRoundTrip[A: JsonWriter: JsonReader](x: A): Unit =
    val jsonString: String = toJsonString(x)
    val jValue: JValue = Parser.parseUnsafe(jsonString)
    val y: A = Converter.fromJson[A](jValue).get
    assert(x == y)

  def toJsonString[A: JsonWriter](x: A): String = CompactPrinter(Converter.toJson(x).get)

end FileInfoSpec
