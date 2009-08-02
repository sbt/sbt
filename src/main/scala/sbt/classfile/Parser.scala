/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt.classfile
import sbt._

import java.io.{DataInputStream, File, InputStream}

// Translation of jdepend.framework.ClassFileParser by Mike Clark, Clarkware Consulting, Inc.
// BSD Licensed
//
// Note that unlike the rest of sbt, some things might be null.

import Constants._

private[sbt] object Parser
{
	def apply(file: File, log: Logger): ClassFile = FileUtilities.readStreamValue(file, log)(parse(file.getCanonicalPath, log)).right.get
	private def parse(fileName: String, log: Logger)(is: InputStream): Either[String, ClassFile] = Right(parseImpl(fileName, is, log))
	private def parseImpl(filename: String, is: InputStream, log: Logger): ClassFile =
	{
		val in = new DataInputStream(is)
		new ClassFile
		{
			assume(in.readInt() == JavaMagic, "Invalid class file: " + fileName)
			
			val fileName = filename
			val minorVersion: Int = in.readUnsignedShort()
			val majorVersion: Int = in.readUnsignedShort()

			val constantPool = parseConstantPool(in)
			val accessFlags: Int = in.readUnsignedShort()

			val className = getClassConstantName(in.readUnsignedShort())
			val superClassName = getClassConstantName(in.readUnsignedShort())
			val interfaceNames = array(in.readUnsignedShort())(getClassConstantName(in.readUnsignedShort()))

			val fields = readFieldsOrMethods()
			val methods = readFieldsOrMethods()

			val attributes = array(in.readUnsignedShort())(parseAttribute())

			lazy val sourceFile =
				for(sourceFileAttribute <- attributes.find(_.isSourceFile)) yield
					toUTF8(entryIndex(sourceFileAttribute))

			def stringValue(a: AttributeInfo) = toUTF8(entryIndex(a))

			private def readFieldsOrMethods() = array(in.readUnsignedShort())(parseFieldOrMethodInfo())
			private def toUTF8(entryIndex: Int) =
			{
				val entry = constantPool(entryIndex)
				assume(entry.tag == ConstantUTF8, "Constant pool entry is not a UTF8 type: " + entryIndex)
				entry.value.get.asInstanceOf[String]
			}
			private def getClassConstantName(entryIndex: Int) =
			{
				val entry = constantPool(entryIndex)
				if(entry == null) ""
				else slashesToDots(toUTF8(entry.nameIndex))
			}
			private def toString(index: Int) =
			{
				if(index <= 0) None
				else Some(toUTF8(index))
			}
			private def parseFieldOrMethodInfo() =
				new FieldOrMethodInfo(in.readUnsignedShort(), toString(in.readUnsignedShort()), toString(in.readUnsignedShort()),
					array(in.readUnsignedShort())(parseAttribute()) )
			private def parseAttribute() =
			{
				val nameIndex = in.readUnsignedShort()
				val name = if(nameIndex == -1) None else Some(toUTF8(nameIndex))
				val value = array(in.readInt())(in.readByte())
				new AttributeInfo(name, value)
			}

			def types = Set((fieldTypes ++ methodTypes ++ classConstantReferences) : _*)

			private def getTypes(fieldsOrMethods: Array[FieldOrMethodInfo]) =
				fieldsOrMethods.flatMap { fieldOrMethod =>
					descriptorToTypes(fieldOrMethod.descriptor)
				}
			
			private def fieldTypes = getTypes(fields)
			private def methodTypes = getTypes(methods)
			
			private def classConstantReferences =
				constants.flatMap { constant =>
					constant.tag match
					{
						case ConstantClass =>
							val name = toUTF8(constant.nameIndex)
							if(name.startsWith("["))
								descriptorToTypes(Some(name))
							else
								slashesToDots(name) :: Nil
						case _ => Nil
					}
				}
			private def constants =
			{
				def next(i: Int, list: List[Constant]): List[Constant] =
				{
					if(i < constantPool.length)
					{
						val constant = constantPool(i)
						next(if(constant.wide) i+2 else i+1, constant :: list)
					}
					else
						list
				}
				next(1, Nil)
			}
		}
    }
    private def array[T](size: Int)(f: => T) = Array.fromFunction(i => f)(size)
	private def parseConstantPool(in: DataInputStream) =
	{
		val constantPoolSize = in.readUnsignedShort()
		val pool = new Array[Constant](constantPoolSize)
		
		def parse(i: Int): Unit =
			if(i < constantPoolSize)
			{
				val constant = getConstant(in)
				pool(i) = constant
				parse( if(constant.wide) i+2 else i+1 )
			}
		
		parse(1) // to lookup: why 1?
		pool
	}

	private def getConstant(in: DataInputStream) =
	{
		val tag = in.readByte()
		tag match
		{
			case ConstantClass | ConstantString => new Constant(tag, in.readUnsignedShort())
			case ConstantField | ConstantMethod | ConstantInterfaceMethod | ConstantNameAndType => 
				new Constant(tag, in.readUnsignedShort(), in.readUnsignedShort())
			case ConstantInteger => new Constant(tag, new java.lang.Integer(in.readInt()))
			case ConstantFloat => new Constant(tag, new java.lang.Float(in.readFloat()))
			case ConstantLong => new Constant(tag, new java.lang.Long(in.readLong()))
			case ConstantDouble => new Constant(tag, new java.lang.Double(in.readDouble()))
			case ConstantUTF8 => new Constant(tag, in.readUTF())
			case _ => error("Unknown constant: " + tag)
		}
	}

	private def toInt(v: Byte) = if(v < 0) v + 256 else v.toInt
	private def entryIndex(a: AttributeInfo) =
	{
		val Array(v0, v1) = a.value
		toInt(v0) * 256 + toInt(v1)
	}

	private def slashesToDots(s: String) = s.replace('/', '.')

	private def descriptorToTypes(descriptor: Option[String]) =
	{
		def toTypes(descriptor: String, types: List[String]): List[String] =
		{
			val startIndex = descriptor.indexOf(ClassDescriptor)
			if(startIndex < 0)
				types
			else
			{
				val endIndex = descriptor.indexOf(';', startIndex+1)
				val tpe = slashesToDots(descriptor.substring(startIndex + 1, endIndex))
				toTypes(descriptor.substring(endIndex), tpe :: types)
			}
		}
		toTypes(descriptor.getOrElse(""), Nil)
    }
}
