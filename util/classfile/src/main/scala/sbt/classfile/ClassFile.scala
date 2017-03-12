/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt
package classfile

import Constants._
import java.io.File

private[sbt] trait ClassFile {
  val majorVersion: Int
  val minorVersion: Int
  val className: String
  val superClassName: String
  val interfaceNames: Array[String]
  val accessFlags: Int
  val constantPool: Array[Constant]
  val fields: Array[FieldOrMethodInfo]
  val methods: Array[FieldOrMethodInfo]
  val attributes: Array[AttributeInfo]
  val sourceFile: Option[String]
  def types: Set[String]
  def stringValue(a: AttributeInfo): String

  /**
    * If the given fieldName represents a ConstantValue field, parses its representation from
    * the constant pool and returns it.
    */
  def constantValue(fieldName: String): Option[AnyRef] =
    this.fields
      .find(_.name.exists(_ == fieldName))
      .toSeq
      .flatMap(_.attributes)
      .collectFirst {
        case ai @ classfile.AttributeInfo(Some("ConstantValue"), _) =>
          constantPool(Parser.entryIndex(ai))
      }
      .map {
        case Constant(ConstantString, nextOffset, _, _) =>
          // follow the indirection from ConstantString to ConstantUTF8
          val nextConstant = constantPool(nextOffset)
          nextConstant.value.getOrElse {
            throw new IllegalStateException(s"Empty UTF8 value in constant pool: $nextConstant")
          }
        case constant @ Constant(
              (ConstantFloat | ConstantLong | ConstantDouble | ConstantInteger),
              _,
              _,
              ref
            ) =>
          ref.getOrElse {
            throw new IllegalStateException(s"Empty primitive value in constant pool: $constant")
          }
        case constant =>
          throw new IllegalStateException(s"Unsupported ConstantValue type: $constant")
      }
}

private[sbt] final case class Constant(tag: Byte, nameIndex: Int, typeIndex: Int, value: Option[AnyRef])
    extends NotNull {
  def this(tag: Byte, nameIndex: Int, typeIndex: Int) = this(tag, nameIndex, typeIndex, None)
  def this(tag: Byte, nameIndex: Int) = this(tag, nameIndex, -1)
  def this(tag: Byte, value: AnyRef) = this(tag, -1, -1, Some(value))
  def wide = tag == ConstantLong || tag == ConstantDouble
}
private[sbt] final case class FieldOrMethodInfo(accessFlags: Int,
                                                name: Option[String],
                                                descriptor: Option[String],
                                                attributes: IndexedSeq[AttributeInfo])
    extends NotNull {
  def isStatic = (accessFlags & ACC_STATIC) == ACC_STATIC
  def isPublic = (accessFlags & ACC_PUBLIC) == ACC_PUBLIC
  def isMain = isPublic && isStatic && descriptor.exists(_ == "([Ljava/lang/String;)V")
}
private[sbt] final case class AttributeInfo(name: Option[String], value: Array[Byte]) extends NotNull {
  def isNamed(s: String) = name.exists(s == _)
  def isSignature = isNamed("Signature")
  def isSourceFile = isNamed("SourceFile")
}
private[sbt] object Constants {
  final val ACC_STATIC = 0x0008
  final val ACC_PUBLIC = 0x0001

  final val JavaMagic = 0xCAFEBABE
  final val ConstantUTF8 = 1
  final val ConstantUnicode = 2
  final val ConstantInteger = 3
  final val ConstantFloat = 4
  final val ConstantLong = 5
  final val ConstantDouble = 6
  final val ConstantClass = 7
  final val ConstantString = 8
  final val ConstantField = 9
  final val ConstantMethod = 10
  final val ConstantInterfaceMethod = 11
  final val ConstantNameAndType = 12
  final val ConstantMethodHandle = 15
  final val ConstantMethodType = 16
  final val ConstantInvokeDynamic = 18
  final val ClassDescriptor = 'L'
}
