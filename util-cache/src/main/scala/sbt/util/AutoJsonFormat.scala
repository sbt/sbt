/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import java.lang.reflect.{ Field, Modifier }
import scala.util.Try
import sjsonnew.{ JsonFormat, Builder, Unbuilder, DeserializationException }
import sjsonnew.BasicJsonProtocol.*
import scala.collection.mutable

/**
 * Runtime JsonFormat derivation for common sbt types to address issue #8288.
 *
 * This provides automatic JsonFormat instances for types that would otherwise
 * require users to use Def.uncached(), helping with sbt 2.0 migration.
 */
object AutoJsonFormat {

  /**
   * Creates a JsonFormat for case classes using runtime reflection.
   * This works for simple case classes with standard field types.
   */
  def caseClassFormat[T](using cls: Class[T]): JsonFormat[T] = new JsonFormat[T] {
    private val fields = cls.getDeclaredFields
      .filter(f => !Modifier.isStatic(f.getModifiers))
      .map { f =>
        f.setAccessible(true)
        f
      }

    def write[J](obj: T, builder: Builder[J]): Unit = {
      builder.beginObject()
      fields.foreach { field =>
        val value = field.get(obj)
        val fieldName = field.getName
        writeField(value, fieldName, builder)
      }
      builder.endObject()
    }

    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T = {
      jsOpt match {
        case Some(js) =>
          unbuilder.beginObject(js)
          val fieldValues = mutable.Map[String, Any]()

          // Try to read each field
          fields.foreach { field =>
            val fieldName = field.getName
            try {
              val value = readField(unbuilder, fieldName, field.getType)
              fieldValues += fieldName -> value
            } catch {
              case _: DeserializationException =>
              // Optional field, skip
            }
          }

          unbuilder.endObject()

          // Create instance using reflection
          val constructor = cls.getDeclaredConstructor()
          constructor.setAccessible(true)
          val instance = constructor.newInstance()

          // Set field values
          fieldValues.foreach { case (fieldName, value) =>
            val field = cls.getDeclaredField(fieldName)
            field.setAccessible(true)
            field.set(instance, value)
          }

          instance
        case None =>
          throw new DeserializationException(
            s"Expected JSON object but found None for ${cls.getSimpleName}"
          )
      }
    }

    private def writeField[J](value: Any, fieldName: String, builder: Builder[J]): Unit = {
      value match {
        case s: String        => builder.addField(fieldName, s)
        case i: Int           => builder.addField(fieldName, i)
        case l: Long          => builder.addField(fieldName, l)
        case d: Double        => builder.addField(fieldName, d)
        case b: Boolean       => builder.addField(fieldName, b)
        case arr: Array[Byte] => builder.addField(fieldName, arr)
        case seq: Seq[?]      => builder.addField(fieldName, seq.toString)
        case opt: Option[?] =>
          opt.foreach(v => writeField(v, fieldName, builder))
        case null  => // skip null fields
        case other =>
          // For complex objects, use toString as fallback
          builder.addField(fieldName, other.toString)
      }
    }

    private def readField[J](
        unbuilder: Unbuilder[J],
        fieldName: String,
        fieldType: Class[?]
    ): Any = {
      fieldType match {
        case c if c == classOf[String]      => unbuilder.readField[String](fieldName)
        case c if c == classOf[Int]         => unbuilder.readField[Int](fieldName)
        case c if c == classOf[Long]        => unbuilder.readField[Long](fieldName)
        case c if c == classOf[Double]      => unbuilder.readField[Double](fieldName)
        case c if c == classOf[Boolean]     => unbuilder.readField[Boolean](fieldName)
        case c if c == classOf[Array[Byte]] => unbuilder.readField[Array[Byte]](fieldName)
        case _                              =>
          // For unsupported types, try string conversion
          unbuilder.readField[String](fieldName)
      }
    }
  }

  /**
   * Fallback JsonFormat that provides helpful error messages
   */
  def fallbackFormat[T](typeName: String): JsonFormat[T] = new JsonFormat[T] {
    def write[J](obj: T, builder: Builder[J]): Unit = {
      throw new UnsupportedOperationException(
        s"""Cannot serialize $typeName. 
           |Consider using Def.uncached() or providing an explicit JsonFormat.
           |For sbt internal types, you may need to add the format to AutoJsonFormats.""".stripMargin
      )
    }

    def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): T = {
      throw new UnsupportedOperationException(
        s"""Cannot deserialize $typeName.
           |Consider using Def.uncached() or providing an explicit JsonFormat.
           |For sbt internal types, you may need to add the format to AutoJsonFormats.""".stripMargin
      )
    }
  }

  /**
   * Try to create a JsonFormat for the given type
   */
  def apply[T](using cls: Class[T]): JsonFormat[T] = {
    if (cls.getSimpleName.startsWith("xsbti") || cls.getName.contains("compile")) {
      // For xsbti types, try case class format first, then fallback
      Try(caseClassFormat[T]).getOrElse(fallbackFormat[T](cls.getSimpleName))
    } else if (scala.util.Try(cls.getDeclaredConstructor()).isSuccess) {
      // For types with default constructor, try case class format
      caseClassFormat[T]
    } else {
      // Use fallback for complex types
      fallbackFormat[T](cls.getSimpleName)
    }
  }
}
