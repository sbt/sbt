/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */
package sbt.librarymanagement

import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }

/** Configures how a module will be cross-versioned. */
abstract class CrossVersion() extends Serializable {
  override def equals(o: Any): Boolean = o match {
    case _: CrossVersion => true
    case _               => false
  }
  override def hashCode: Int = {
    37 * (17 + "sbt.librarymanagement.CrossVersion".##)
  }
  override def toString: String = {
    "CrossVersion()"
  }
}
object CrossVersion extends sbt.librarymanagement.CrossVersionFunctions {}

/** Disables cross versioning for a module. */
sealed class Disabled private () extends sbt.librarymanagement.CrossVersion() with Serializable {

  override def equals(o: Any): Boolean = o match {
    case _: Disabled => true
    case _           => false
  }
  override def hashCode: Int = {
    37 * (17 + "sbt.librarymanagement.Disabled".##)
  }
  override def toString: String = {
    "Disabled()"
  }
  private[this] def copy(): Disabled = {
    new Disabled()
  }

}
object Disabled extends sbt.librarymanagement.Disabled {
  def apply(): Disabled = Disabled
}

/**
 * Cross-versions a module using the result of
 * prepending `prefix` and appending `suffix` to the binary version.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the binary version is "2.10",
 * the module is cross-versioned with "foo_2.10_bar".
 */
final class Binary private (val prefix: String, val suffix: String)
    extends sbt.librarymanagement.CrossVersion()
    with Serializable {

  private def this() = this("", "")

  override def equals(o: Any): Boolean = o match {
    case x: Binary => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _         => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.Binary".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "Binary(" + prefix + ", " + suffix + ")"
  }
  private[this] def copy(prefix: String = prefix, suffix: String = suffix): Binary = {
    new Binary(prefix, suffix)
  }
  def withPrefix(prefix: String): Binary = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): Binary = {
    copy(suffix = suffix)
  }
}
object Binary {

  def apply(): Binary = new Binary()
  def apply(prefix: String, suffix: String): Binary = new Binary(prefix, suffix)
}

/** Cross-versions a module using the string `value`. */
final class Constant private (val value: String)
    extends sbt.librarymanagement.CrossVersion()
    with Serializable {

  private def this() = this("")

  override def equals(o: Any): Boolean = o match {
    case x: Constant => (this.value == x.value)
    case _           => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.librarymanagement.Constant".##) + value.##)
  }
  override def toString: String = {
    "Constant(" + value + ")"
  }
  private[this] def copy(value: String = value): Constant = {
    new Constant(value)
  }
  def withValue(value: String): Constant = {
    copy(value = value)
  }
}
object Constant {
  def apply(): Constant = new Constant()
  def apply(value: String): Constant = new Constant(value)
}

/**
 * Cross-versions a module by stripping off -bin-suffix.
 * This is intended for patch-version compatible alternative replacements.
 */
final class Patch private () extends sbt.librarymanagement.CrossVersion() with Serializable {

  override def equals(o: Any): Boolean = o match {
    case _: Patch => true
    case _        => false
  }
  override def hashCode: Int = {
    37 * (17 + "sbt.librarymanagement.Patch".##)
  }
  override def toString: String = {
    "Patch()"
  }
  private[this] def copy(): Patch = {
    new Patch()
  }
}

object Patch {
  def apply(): Patch = new Patch()
}

/**
 * Cross-versions a module with the result of
 * prepending `prefix` and appending `suffix` to the full version.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the full version is "2.12.1",
 * the module is cross-versioned with "foo_2.12.1_bar".
 */
final class Full private (val prefix: String, val suffix: String)
    extends sbt.librarymanagement.CrossVersion()
    with Serializable {

  private def this() = this("", "")

  override def equals(o: Any): Boolean = o match {
    case x: Full => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _       => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.Full".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "Full(" + prefix + ", " + suffix + ")"
  }
  private[this] def copy(prefix: String = prefix, suffix: String = suffix): Full = {
    new Full(prefix, suffix)
  }
  def withPrefix(prefix: String): Full = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): Full = {
    copy(suffix = suffix)
  }
}

object Full {
  def apply(): Full = new Full()
  def apply(prefix: String, suffix: String): Full = new Full(prefix, suffix)
}

/**
 * Similar to Binary except that if the binary version is 3
 * (or if it is of the form 3.0.0-x) it uses 2.13 instead.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the binary version is "3",
 * the module is cross-versioned with "foo_2.13_bar".
 */
final class For3Use2_13 private (val prefix: String, val suffix: String)
    extends sbt.librarymanagement.CrossVersion()
    with Serializable {

  private def this() = this("", "")

  override def equals(o: Any): Boolean = o match {
    case x: For3Use2_13 => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _              => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.For3Use2_13".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "For3Use2_13(" + prefix + ", " + suffix + ")"
  }
  private[this] def copy(prefix: String = prefix, suffix: String = suffix): For3Use2_13 = {
    new For3Use2_13(prefix, suffix)
  }
  def withPrefix(prefix: String): For3Use2_13 = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): For3Use2_13 = {
    copy(suffix = suffix)
  }
}
object For3Use2_13 {

  def apply(): For3Use2_13 = new For3Use2_13()
  def apply(prefix: String, suffix: String): For3Use2_13 = new For3Use2_13(prefix, suffix)
}

/**
 * Similar to Binary except that if the binary version is 2.13
 * it uses 3 instead.
 * For example, if `prefix = "foo_"` and `suffix = "_bar"` and the binary version is "2.13",
 * the module is cross-versioned with "foo_3_bar".
 */
final class For2_13Use3 private (val prefix: String, val suffix: String)
    extends sbt.librarymanagement.CrossVersion()
    with Serializable {

  private def this() = this("", "")

  override def equals(o: Any): Boolean = o match {
    case x: For2_13Use3 => (this.prefix == x.prefix) && (this.suffix == x.suffix)
    case _              => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.For3Use2_13".##) + prefix.##) + suffix.##)
  }
  override def toString: String = {
    "For3Use2_13(" + prefix + ", " + suffix + ")"
  }
  private[this] def copy(prefix: String = prefix, suffix: String = suffix): For2_13Use3 = {
    new For2_13Use3(prefix, suffix)
  }
  def withPrefix(prefix: String): For2_13Use3 = {
    copy(prefix = prefix)
  }
  def withSuffix(suffix: String): For2_13Use3 = {
    copy(suffix = suffix)
  }
}
object For2_13Use3 {

  def apply(): For2_13Use3 = new For2_13Use3()
  def apply(prefix: String, suffix: String): For2_13Use3 = new For2_13Use3(prefix, suffix)
}

trait DisabledFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val DisabledFormat: JsonFormat[sbt.librarymanagement.Disabled] =
    new JsonFormat[sbt.librarymanagement.Disabled] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Disabled = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)

            unbuilder.endObject()
            sbt.librarymanagement.Disabled()
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Disabled, builder: Builder[J]): Unit = {
        builder.beginObject()

        builder.endObject()
      }
    }

  implicit lazy val DisabledObjectFormat: JsonFormat[sbt.librarymanagement.Disabled.type] =
    new JsonFormat[sbt.librarymanagement.Disabled.type] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Disabled.type = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)

            unbuilder.endObject()
            sbt.librarymanagement.Disabled
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Disabled.type, builder: Builder[J]): Unit = {
        builder.beginObject()

        builder.endObject()
      }
    }
}

trait BinaryFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val BinaryFormat: JsonFormat[sbt.librarymanagement.Binary] =
    new JsonFormat[sbt.librarymanagement.Binary] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Binary = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val prefix = unbuilder.readField[String]("prefix")
            val suffix = unbuilder.readField[String]("suffix")
            unbuilder.endObject()
            sbt.librarymanagement.Binary(prefix, suffix)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Binary, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("prefix", obj.prefix)
        builder.addField("suffix", obj.suffix)
        builder.endObject()
      }
    }
}

trait ConstantFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val ConstantFormat: JsonFormat[sbt.librarymanagement.Constant] =
    new JsonFormat[sbt.librarymanagement.Constant] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Constant = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val value = unbuilder.readField[String]("value")
            unbuilder.endObject()
            sbt.librarymanagement.Constant(value)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Constant, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("value", obj.value)
        builder.endObject()
      }
    }
}

trait PatchFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val PatchFormat: JsonFormat[sbt.librarymanagement.Patch] =
    new JsonFormat[sbt.librarymanagement.Patch] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Patch = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)

            unbuilder.endObject()
            sbt.librarymanagement.Patch()
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Patch, builder: Builder[J]): Unit = {
        builder.beginObject()

        builder.endObject()
      }
    }
}

trait FullFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val FullFormat: JsonFormat[sbt.librarymanagement.Full] =
    new JsonFormat[sbt.librarymanagement.Full] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.Full = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val prefix = unbuilder.readField[String]("prefix")
            val suffix = unbuilder.readField[String]("suffix")
            unbuilder.endObject()
            sbt.librarymanagement.Full(prefix, suffix)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.Full, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("prefix", obj.prefix)
        builder.addField("suffix", obj.suffix)
        builder.endObject()
      }
    }
}

trait For3Use2_13Formats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val For3Use2_13Format: JsonFormat[sbt.librarymanagement.For3Use2_13] =
    new JsonFormat[sbt.librarymanagement.For3Use2_13] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.For3Use2_13 = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val prefix = unbuilder.readField[String]("prefix")
            val suffix = unbuilder.readField[String]("suffix")
            unbuilder.endObject()
            sbt.librarymanagement.For3Use2_13(prefix, suffix)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.For3Use2_13, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("prefix", obj.prefix)
        builder.addField("suffix", obj.suffix)
        builder.endObject()
      }
    }
}

trait For2_13Use3Formats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val For2_13Use3Format: JsonFormat[sbt.librarymanagement.For2_13Use3] =
    new JsonFormat[sbt.librarymanagement.For2_13Use3] {
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.For2_13Use3 = {
        jsOpt match {
          case Some(js) =>
            unbuilder.beginObject(js)
            val prefix = unbuilder.readField[String]("prefix")
            val suffix = unbuilder.readField[String]("suffix")
            unbuilder.endObject()
            sbt.librarymanagement.For2_13Use3(prefix, suffix)
          case None =>
            deserializationError("Expected JsObject but found None")
        }
      }
      override def write[J](obj: sbt.librarymanagement.For2_13Use3, builder: Builder[J]): Unit = {
        builder.beginObject()
        builder.addField("prefix", obj.prefix)
        builder.addField("suffix", obj.suffix)
        builder.endObject()
      }
    }
}

trait CrossVersionFormats {
  self: sjsonnew.BasicJsonProtocol
    with sbt.librarymanagement.DisabledFormats
    with sbt.librarymanagement.BinaryFormats
    with sbt.librarymanagement.ConstantFormats
    with sbt.librarymanagement.PatchFormats
    with sbt.librarymanagement.FullFormats
    with sbt.librarymanagement.For3Use2_13Formats
    with sbt.librarymanagement.For2_13Use3Formats =>
  implicit lazy val CrossVersionFormat: JsonFormat[CrossVersion] = {
    val format = flatUnionFormat8[
      CrossVersion,
      Disabled,
      Disabled.type,
      Binary,
      Constant,
      Patch,
      Full,
      For3Use2_13,
      For2_13Use3
    ]("type")
    // This is a hand-crafted formatter to avoid Disabled$ showing up in JSON
    new JsonFormat[CrossVersion] {
      override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CrossVersion =
        format.read(jsOpt, unbuilder)
      override def write[J](obj: CrossVersion, builder: Builder[J]): Unit = {
        if (obj == Disabled) {
          builder.beginPreObject()
          builder.addFieldName("type")
          builder.writeString("Disabled")
          builder.endPreObject()
          builder.beginObject()
          builder.endObject()
        } else format.write(obj, builder)
      }
    }
  }
}
