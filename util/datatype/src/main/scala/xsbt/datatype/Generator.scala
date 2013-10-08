/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt
package datatype

import java.io.File
import sbt.Path
import sbt.IO.write

import Generator._
import java.util.Locale

abstract class GeneratorBase(val basePkgName: String, val baseDirectory: File)
{
	def writeDefinitions(ds: Iterable[Definition]) = Generator.writeDefinitions(ds)(writeDefinition)
	def writeDefinition(d: Definition) = d match { case e: EnumDef => writeEnum(e); case c: ClassDef => writeClass(c) }
	def writeEnum(e: EnumDef)
	{
		val content =
			"public enum " + e.name + " {" +
				e.members.mkString("\n\t", ",\n\t", "\n") +
			"}"
		writeSource(e.name, basePkgName, content)
	}
	def writeClass(c: ClassDef): Unit

	def writeSource(name: String, pkgName: String, content: String)
	{
		val file = new File(new File(baseDirectory, packagePath(pkgName)), name+ ".java")
		file.getParentFile.mkdirs()
		write(file, "package " + pkgName + ";\n\n" + content)
	}
	private def packagePath(pkgName: String) = pkgName.replace('.', File.separatorChar)
}

/** Creates immutable datatype classes in Java from the intermediate Definition representation.
*
* A ClassDef is written as a class with an optional parent class.  The class has a single constructor with
* parameters for all declared and inherited members.  Declared members are listed first in the constructor.
* Inherited members are passed to the super constructor.  The value of each member is held in a private
* final field and is accessed by a method of the same name.
*
* A toString method is generated for debugging.
* The classes implement java.io.Serializable.
*
*.@param baseDirectory output directory for sources
* @param pkgName package that classes will be defined in*/
class ImmutableGenerator(pkgName: String, baseDir: File) extends GeneratorBase(pkgName, baseDir)
{
	def writeClass(c: ClassDef): Unit = writeSource(c.name, basePkgName, classContent(c))
	def classContent(c: ClassDef): String =
	{
		val hasParent = c.parent.isDefined
		val allMembers = c.allMembers.map(normalize)
		val normalizedMembers = c.members.map(normalize)
		val fields = normalizedMembers.map(m => "private final " + m.asJavaDeclaration(false) + ";")
		val accessors = normalizedMembers.map(m => "public final " + m.asJavaDeclaration(true) + "()\n\t{\n\t\treturn " + m.asGet + ";\n\t}")
		val parameters = allMembers.map(_.asJavaDeclaration(false))
		val assignments = normalizedMembers.map(m => "this." + m.name + " = " + m.name + ";")
		val superConstructor =
		{
			val inherited = c.inheritedMembers
			if(inherited.isEmpty) "" else  "super(" + inherited.map(_.name).mkString(", ") + ");\n\t\t"
		}

		val constructor = "public " + c.name + "("  + parameters.mkString(", ") + ")\n\t" +
		"{\n\t\t" +
			superConstructor +
			assignments.mkString("\n\t\t") + "\n\t" +
		"}"
		"import java.util.Arrays;\n" +
		"import java.util.List;\n" +
		"public class " + c.name + c.parent.map(" extends " + _.name + " ").getOrElse(" implements java.io.Serializable") + "\n" +
		"{\n\t" +
			constructor + "\n\t" + 
			(fields ++ accessors).mkString("\n\t") + "\n\t" +
			toStringMethod(c) + "\n" +
		"}\n"
	}

}

object Generator
{
	def methodSignature(modifiers: String, returnType: String, name: String, parameters: String) =
		modifiers + " " + returnType + " " + name + "(" + parameters + ")"
	def method(modifiers: String, returnType: String, name: String, parameters: String, content: String) =
		methodSignature(modifiers, returnType, name, parameters) + "\n\t{\n\t\treturn " + content + ";\n\t}"
	def fieldToString(name: String, single: Boolean) = "\"" + name + ": \" + " + fieldString(name + "()", single)
	def fieldString(arg: String, single: Boolean) = if(single) arg else "Arrays.toString(" + arg + ")"
	def normalize(m: MemberDef): MemberDef =
		m.mapType(tpe => if(primitives(tpe.toLowerCase(Locale.ENGLISH))) tpe.toLowerCase(Locale.ENGLISH) else tpe)
	private val primitives = Set("int", "boolean", "float", "long", "short", "byte", "char", "double")

	def toStringMethod(c: ClassDef): String =
	{
		val allMembers = c.allMembers.map(normalize)
		val parametersString = if(allMembers.isEmpty) "\"\"" else allMembers.map(m => fieldToString(m.name, m.single)).mkString(" + \", \" + ")
		method("public", "String", "toString", "", "\"" + c.name + "(\" + " + parametersString + "+ \")\"")
	}

	def writeDefinitions(ds: Iterable[Definition])(writeDefinition: Definition => Unit)
	{
		val (_, duplicates) =
			( (Set[String](), Set[String]()) /: ds.map(_.name)) {
				case ((nameSet, duplicates), name) =>
					if(nameSet.contains(name)) (nameSet, duplicates + name) else (nameSet + name, duplicates)
			}
		if(duplicates.isEmpty)
			ds.foreach(writeDefinition)
		else
			sys.error("Duplicate names:\n\t" + duplicates.mkString("\n\t"))
	}
	def implName(name: String) = name + "0"
}