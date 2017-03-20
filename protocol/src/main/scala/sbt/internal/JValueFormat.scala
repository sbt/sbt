/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package sbt
package internal

import sjsonnew.{ JsonWriter => JW, JsonReader => JR, JsonFormat => JF, _ }
import scala.json.ast.unsafe._

trait JValueFormat { self: sjsonnew.BasicJsonProtocol =>
  /** Define a JsonWriter for the type T wrapper of underlying type U given JsonWriter[U] and T => U. */
  def unlift[T, U](unlift: T => U)(implicit z: JW[U]): JW[T] = new JW[T] {
    def write[J](w: T, b: Builder[J]) = z.write(unlift(w), b)
  }

  /** Define a JsonReader for the type T wrapper of underlying type U given JsonReader[U] and U => T. */
  def lift[T, U](lift: U => T)(implicit z: JR[U]): JR[T] = new JR[T] {
    def read[J](j: Option[J], u: Unbuilder[J]): T = lift(z.read(j, u))
  }

  @inline def ?[A](implicit z: A): A = z

  implicit val JNullJW: JW[JNull.type] = new JW[JNull.type] { def write[J](x: JNull.type, b: Builder[J]) = b.writeNull() }
  implicit val JNullJR: JR[JNull.type] = new JR[JNull.type] { def read[J](j: Option[J], u: Unbuilder[J]) = JNull }

  implicit val JBooleanJW: JW[JBoolean] = unlift(_.get)
  implicit val JBooleanJR: JR[JBoolean] = lift(JBoolean(_))

  implicit val JStringJW: JW[JString] = unlift(_.value)
  implicit val JStringJR: JR[JString] = lift(JString(_))

  implicit val JNumberJW: JW[JNumber] = unlift(x => BigDecimal(x.value))
  implicit val JNumberJR: JR[JNumber] = lift((x: BigDecimal) => JNumber(x.toString))

  implicit lazy val JArrayJW: JW[JArray] = unlift[JArray, Array[JValue]](_.value)

  implicit lazy val JObjectJW: JW[JObject] = new JW[JObject] {
    def write[J](x: JObject, b: Builder[J]) = {
      b.beginObject()
      x.value foreach (jsonField => JValueJW.addField(jsonField.field, jsonField.value, b))
      b.endObject()
    }
  }

  implicit lazy val JValueJW: JW[JValue] = new JW[JValue] {
    def write[J](x: JValue, b: Builder[J]) = x match {
      case x: JNull.type => ?[JW[JNull.type]].write(x, b)
      case x: JBoolean   => ?[JW[JBoolean]].write(x, b)
      case x: JString    => ?[JW[JString]].write(x, b)
      case x: JNumber    => ?[JW[JNumber]].write(x, b)
      case x: JObject    => ?[JW[JObject]].write(x, b)
      case x: JArray     => ?[JW[JArray]].write(x, b)
    }
  }

  implicit lazy val JValueJR: JR[JValue] = new JR[JValue] {
    def read[J](j: Option[J], u: Unbuilder[J]) = ??? // Is this even possible? with no Manifest[J]?
  }

  implicit lazy val JValueJF: JF[JValue] = jsonFormat[JValue](JValueJR, JValueJW)
}
