/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package util.codec

import sjsonnew.{ JsonWriter => JW, JsonReader => JR, JsonFormat => JF, _ }
import sjsonnew.shaded.scalajson.ast.unsafe._

trait JValueFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit val JNullFormat: JF[JNull.type] = new JF[JNull.type] {
    def write[J](x: JNull.type, b: Builder[J]) = b.writeNull()
    def read[J](j: Option[J], u: Unbuilder[J]) = JNull
  }

  implicit val JBooleanFormat: JF[JBoolean] = projectFormat(_.get, (x: Boolean) => JBoolean(x))
  implicit val JStringFormat: JF[JString] = projectFormat(_.value, (x: String) => JString(x))

  implicit val JNumberFormat: JF[JNumber] =
    projectFormat(x => BigDecimal(x.value), (x: BigDecimal) => JNumber(x.toString))

  implicit val JArrayFormat: JF[JArray] = projectFormat[JArray, Array[JValue]](_.value, JArray(_))

  implicit lazy val JObjectJsonWriter: JW[JObject] = new JW[JObject] {
    def write[J](x: JObject, b: Builder[J]) = {
      b.beginObject()
      x.value foreach (jsonField => JValueFormat.addField(jsonField.field, jsonField.value, b))
      b.endObject()
    }
  }

  implicit lazy val JValueJsonWriter: JW[JValue] = new JW[JValue] {
    def write[J](x: JValue, b: Builder[J]) = x match {
      case x: JNull.type => JNullFormat.write(x, b)
      case x: JBoolean   => JBooleanFormat.write(x, b)
      case x: JString    => JStringFormat.write(x, b)
      case x: JNumber    => JNumberFormat.write(x, b)
      case x: JArray     => JArrayFormat.write(x, b)
      case x: JObject    => JObjectJsonWriter.write(x, b)
    }
  }

  // This passes through JValue, or returns JNull instead of blowing up with unimplemented.
  implicit lazy val JValueJsonReader: JR[JValue] = new JR[JValue] {
    def read[J](j: Option[J], u: Unbuilder[J]) = j match {
      case Some(x: JValue) => x
      case Some(x)         => sys.error(s"Uknown AST $x")
      case _               => JNull
    }
  }

  implicit lazy val JValueFormat: JF[JValue] =
    jsonFormat[JValue](JValueJsonReader, JValueJsonWriter)
}
