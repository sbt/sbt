package xsbt.api

import java.io.File
import xsbt.FileUtilities

/** Creates immutable datatype classes in Java from the intermediate Definition representation.
*
* A ClassDef is written as a class with an optional parent class.  The class has a single constructor with
* parameters for all declared and inherited members.  Declared members are listed first in the constructor.
* Inherited members are passed to the super constructor.  The value of each member is held in a private
* final field and.is accessed by a method of the same name.
*
* A toString method is generated for debugging.
* The classes implement java.io.Serializable.
*
*.@param baseDirectory output directory for sources
* @param pkgName package that classes will be defined in*/
class Generator(pkgName: String, baseDirectory: File)
{
	def writeDefinitions(ds: Iterable[Definition]) =
	{
		val (_, duplicates) =
			( (Set[String](), Set[String]()) /: ds.map(_.name)) {
				case ((nameSet, duplicates), name) =>
					if(nameSet.contains(name)) (nameSet, duplicates + name) else (nameSet + name, duplicates)
			}
		if(duplicates.isEmpty)
			ds.foreach(writeDefinition)
		else
			error("Duplicate names:\n\t" + duplicates.mkString("\n\t"))
	}
	def writeDefinition(d: Definition) = d match { case e: EnumDef => write(e); case c: ClassDef => write(c) }
	def write(e: EnumDef)
	{
		val content =
			"public enum " + e.name + " {" +
				e.members.mkString("\n\t", ",\n\t", "\n") +
			"}"
		writeSource(e.name, content)
	}
	def write(c: ClassDef): Unit = writeSource(c.name, classContent(c))
	def classContent(c: ClassDef): String =
	{
		val hasParent = c.parent.isDefined
		val allMembers = c.allMembers.map(normalize)
		val normalizedMembers = c.members.map(normalize)
		val fields = normalizedMembers.map(m => "private final " + m.asJavaDeclaration + ";")
		val accessors = normalizedMembers.map(m => "public final " + m.asJavaDeclaration + "()\n\t{\n\t\treturn " + m.name + ";\n\t}")
		val parameters = allMembers.map(_.asJavaDeclaration)
		val assignments = normalizedMembers.map(m => "this." + m.name + " = " + m.name + ";")
		val superConstructor =
		{
			val inherited = c.inheritedMembers
			if(inherited.isEmpty) "" else  "super(" + inherited.map(_.name).mkString(", ") + ");\n\t\t"
		}
		val parametersString = if(allMembers.isEmpty) "\"\"" else allMembers.map(m => fieldToString(m.name, m.single)).mkString(" + \", \" + ")
		val toStringMethod = method("public", "String", "toString", "", "\"" + c.name + "(\" + " + parametersString + "+ \")\"")

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
			toStringMethod + "\n\t" +
		"}"
	}
	def method(modifiers: String, returnType: String, name: String, parameters: String, content: String) =
		modifiers + " " + returnType + " " + name + "(" + parameters + ")\n\t{\n\t\treturn " + content + ";\n\t}"
	def fieldToString(name: String, single: Boolean) = "\"" + name + ": \" + " + fieldString(name + "()", single)
	def fieldString(arg: String, single: Boolean) = if(single) arg else "Arrays.toString(" + arg + ")"
	def normalize(m: MemberDef): MemberDef =
		m.mapType(tpe => if(primitives(tpe.toLowerCase)) tpe.toLowerCase else tpe)

	def writeSource(name: String, content: String)
	{
		import Paths._
		val file =baseDirectory / packagePath / (name+ ".java")
		file.getParentFile.mkdirs()
		FileUtilities.write(file, "package " + pkgName + ";\n\n" + content)
	}
	private def packagePath = pkgName.replace('.', File.separatorChar)
	private val primitives = Set("int", "boolean", "float", "long", "short", "byte", "char", "double")
}
