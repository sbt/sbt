package sbt.util

import scalajson.ast.unsafe._
import sjsonnew._, support.scalajson.unsafe._
import CacheImplicits._
import sbt.internal.util.{ UnitSpec, HNil }

class HListFormatSpec extends UnitSpec {
  val quux = 23 :+: "quux" :+: true :+: HNil

  it should "round trip quux" in assertRoundTrip(quux)
  it should "round trip hnil" in assertRoundTrip(HNil)

  it should "have a flat structure for quux" in assertJsonString(quux, """[23,"quux",true]""")
  it should "have a flat structure for hnil" in assertJsonString(HNil, "[]")

  def assertRoundTrip[A: JsonWriter: JsonReader](x: A) = {
    val jsonString: String = toJsonString(x)
    val jValue: JValue = Parser.parseUnsafe(jsonString)
    val y: A = Converter.fromJson[A](jValue).get
    assert(x === y)
  }

  def assertJsonString[A: JsonWriter](x: A, s: String) = assert(toJsonString(x) === s)

  def toJsonString[A: JsonWriter](x: A): String = CompactPrinter(Converter.toJson(x).get)
}
