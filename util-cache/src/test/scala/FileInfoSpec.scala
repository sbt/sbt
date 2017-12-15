package sbt.util

import sjsonnew.shaded.scalajson.ast.unsafe._
import sjsonnew._, support.scalajson.unsafe._
import sbt.internal.util.UnitSpec
import sbt.io.IO

class FileInfoSpec extends UnitSpec {
  val file = new java.io.File(".").getAbsoluteFile
  val fileInfo: ModifiedFileInfo = FileModified(file, IO.getModifiedTime(file))
  val filesInfo = FilesInfo(Set(fileInfo))

  it should "round trip" in assertRoundTrip(filesInfo)

  def assertRoundTrip[A: JsonWriter: JsonReader](x: A) = {
    val jsonString: String = toJsonString(x)
    val jValue: JValue = Parser.parseUnsafe(jsonString)
    val y: A = Converter.fromJson[A](jValue).get
    assert(x === y)
  }

  def assertJsonString[A: JsonWriter](x: A, s: String) = assert(toJsonString(x) === s)

  def toJsonString[A: JsonWriter](x: A): String = CompactPrinter(Converter.toJson(x).get)
}
