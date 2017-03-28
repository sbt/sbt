/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal

import sjsonnew.{ JsonWriter => JW, JsonReader => JR, JsonFormat => JF, _ }
import scala.json.ast.unsafe._

trait JValueFormat { self: sjsonnew.BasicJsonProtocol =>
  implicit val JNullJF: JF[JNull.type] = new JF[JNull.type] {
    def write[J](x: JNull.type, b: Builder[J]) = b.writeNull()
    def read[J](j: Option[J], u: Unbuilder[J]) = JNull
  }

  implicit val JBooleanJF: JF[JBoolean] = project(_.get, JBoolean(_))
  implicit val JStringJF: JF[JString] = project(_.value, JString(_))
  implicit val JNumberJF: JF[JNumber] = project(x => BigDecimal(x.value), (x: BigDecimal) => JNumber(x.toString))
  implicit val JArrayJF: JF[JArray] = project[JArray, Array[JValue]](_.value, JArray(_))

  implicit lazy val JObjectJW: JW[JObject] = new JW[JObject] {
    def write[J](x: JObject, b: Builder[J]) = {
      b.beginObject()
      x.value foreach (jsonField => JValueJW.addField(jsonField.field, jsonField.value, b))
      b.endObject()
    }
  }

  implicit lazy val JValueJW: JW[JValue] = new JW[JValue] {
    def write[J](x: JValue, b: Builder[J]) = x match {
      case x: JNull.type => JNullJF.write(x, b)
      case x: JBoolean   => JBooleanJF.write(x, b)
      case x: JString    => JStringJF.write(x, b)
      case x: JNumber    => JNumberJF.write(x, b)
      case x: JArray     => JArrayJF.write(x, b)
      case x: JObject    => JObjectJW.write(x, b)
    }
  }

  implicit lazy val JValueJR: JR[JValue] = new JR[JValue] {
    def read[J](j: Option[J], u: Unbuilder[J]) = ??? // Is this even possible? with no Manifest[J]?
  }

  implicit lazy val JValueJF: JF[JValue] = jsonFormat[JValue](JValueJR, JValueJW)
}
