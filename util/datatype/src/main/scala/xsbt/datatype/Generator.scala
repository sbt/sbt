/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt
package datatype

import java.io.File
import sbt.IO.write

import Generator._
import java.util.Locale

abstract class GeneratorBase(val basePkgName: String, val baseDirectory: File) {
  def writeDefinitions(ds: Iterable[Definition]) = Generator.writeDefinitions(ds)(writeDefinition)
  def writeDefinition(d: Definition) = d match { case e: EnumDef => writeEnum(e); case c: ClassDef => writeClass(c) }
  def writeEnum(e: EnumDef): Unit = {
    val content =
      "public enum " + e.name + " {" +
        e.members.mkString("\n\t", ",\n\t", "\n") +
        "}"
    writeSource(e.name, basePkgName, content)
  }
  def writeClass(c: ClassDef): Unit

  def writeSource(name: String, pkgName: String, content: String): Unit = {
    val file = new File(new File(baseDirectory, packagePath(pkgName)), name + ".java")
    file.getParentFile.mkdirs()
    write(file, "package " + pkgName + ";\n\n" + content)
  }
  private def packagePath(pkgName: String) = pkgName.replace('.', File.separatorChar)
}

/**
 * Creates immutable datatype classes in Java from the intermediate Definition representation.
 *
 * A ClassDef is written as a class with an optional parent class.  The class has a single constructor with
 * parameters for all declared and inherited members.  Declared members are listed first in the constructor.
 * Inherited members are passed to the super constructor.  The value of each member is held in a private
 * final field and is accessed by a method of the same name.
 *
 * A toString method is generated for debugging.
 * The classes implement java.io.Serializable.
 *
 * .@param baseDirectory output directory for sources
 * @param pkgName package that classes will be defined in
 */
class ImmutableGenerator(pkgName: String, baseDir: File) extends GeneratorBase(pkgName, baseDir) {
  def writeClass(c: ClassDef): Unit = writeSource(c.name, basePkgName, classContent(c))
  def classContent(c: ClassDef): String =
    {
      val abstractStr = if (c.isAbstract) "abstract " else "final "
      val allMembers = c.allMembers.map(normalize)
      val normalizedMembers = c.members.map(normalize)
      val fields = normalizedMembers.map(m => "private final " + m.asJavaDeclaration(false) + ";")
      val accessors = normalizedMembers.map(m => "public final " + m.asJavaDeclaration(true) + "()\n\t{\n\t\treturn " + m.asGet + ";\n\t}")
      val parameters = allMembers.map(_.asJavaDeclaration(false))
      val assignments = normalizedMembers.map(m => "this." + m.name + " = " + m.name + ";")
      val superConstructor =
        {
          val inherited = c.inheritedMembers
          if (inherited.isEmpty) "" else "super(" + inherited.map(_.name).mkString(", ") + ");\n\t\t"
        }

      val constructor = "public " + c.name + "(" + parameters.mkString(", ") + ")\n\t" +
        "{\n\t\t" +
        superConstructor +
        assignments.mkString("\n\t\t") + "\n\t" +
        "}"
      "import java.util.Arrays;\n" +
        "import java.util.List;\n" +
        "public " + abstractStr + "class " + c.name + c.parent.map(" extends " + _.name + " ").getOrElse(" implements java.io.Serializable") + "\n" +
        "{\n\t" +
        constructor + "\n\t" +
        (fields ++ accessors).mkString("\n\t") + "\n" +
        (if (!c.isAbstract) "\t" + equalsMethod(c) + "\n\t" + hashCodeMethod(c) + "\n\t" + toStringMethod(c) + "\n" else "") +
        "}\n"
    }
}

object Generator {
  def methodSignature(modifiers: String, returnType: String, name: String, parameters: String) =
    modifiers + " " + returnType + " " + name + "(" + parameters + ")"
  def method(modifiers: String, returnType: String, name: String, parameters: String, body: String) =
    methodSignature(modifiers, returnType, name, parameters) + "\n\t{\n\t\t " + body + "\n\t}"
  def fieldToString(name: String, single: Boolean) = "\"" + name + ": \" + " + fieldString(name + "()", single)
  def fieldString(arg: String, single: Boolean) = if (single) arg else "Arrays.toString(" + arg + ")"
  def fieldEquals(arg: String, single: Boolean, primitive: Boolean) = {
    if (single) {
      if (primitive) arg + " == o." + arg else arg + ".equals(o." + arg + ")"
    } else {
      "Arrays." + (if (primitive) "equals" else "deepEquals") + "(" + arg + ", o." + arg + ")"
    }
  }
  def normalize(m: MemberDef): MemberDef =
    m.mapType(tpe => if (isPrimitive(tpe)) tpe.toLowerCase(Locale.ENGLISH) else tpe)
  def isPrimitive(tpe: String) = primitives(tpe.toLowerCase(Locale.ENGLISH))
  private val primitives = Set("int", "boolean", "float", "long", "short", "byte", "char", "double")

  def equalsMethod(c: ClassDef): String =
    {
      val content = if (c.hasLazyMembers) {
        "return this == obj;  // We have lazy members, so use object identity to avoid circularity."
      } else {
        val allMembers = c.allMembers.map(normalize)
        val memberComparisons = if (allMembers.isEmpty) "true" else allMembers.map(m => fieldEquals(m.name + "()", m.single, isPrimitive(m.tpe))).mkString(" && ")
        "if (this == obj) {\n\t\t\t return true;\n\t\t} else if (!(obj instanceof " + c.name + ")) {\n\t\t\t return false;\n\t\t} else {\n\t\t\t" + c.name + " o = (" + c.name + ")obj;\n\t\t\treturn " + memberComparisons + ";\n\t\t}"
      }
      method("public", "boolean", "equals", "Object obj", content)
    }

  def hashCodeMethod(c: ClassDef): String =
    {
      def hashCodeExprForMember(m: MemberDef) =
        {
          val primitive = isPrimitive(m.tpe)
          val f = m.name + "()" // Assumes m has already been normalized.
          if (m.single) {
            if (primitive) {
              m.tpe.toLowerCase match {
                case "boolean" => "(" + f + " ? 0 : 1)"
                case "long"    => "(int)(" + f + " ^ (" + f + " >>> 32))"
                case "float"   => "Float.floatToIntBits(" + f + ")"
                case "double"  => "(int)(Double.doubleToLongBits(" + f + ") ^ (Double.doubleToLongBits(" + f + ") >>> 32))"
                case "int"     => f
                case _         => "(int)" + f
              }
            } else {
              f + ".hashCode()"
            }
          } else {
            "Arrays." + (if (primitive) "hashCode" else "deepHashCode") + "(" + f + ")"
          }
        }
      val hashCodeExpr = if (c.hasLazyMembers) {
        "super.hashCode()"
      } else {
        val allMembers = c.allMembers.map(normalize)
        val memberHashCodes = allMembers.map(hashCodeExprForMember)
        ("17" /: memberHashCodes) { "37 * (" + _ + ") + " + _ }
      }
      method("public", "int", "hashCode", "", "return " + hashCodeExpr + ";")
    }

  def toStringMethod(c: ClassDef): String =
    {
      val content = if (c.hasLazyMembers) {
        "return super.toString();"
      } else {
        val allMembers = c.allMembers.map(normalize)
        val parametersString = if (allMembers.isEmpty) "\"\"" else allMembers.map(m => fieldToString(m.name, m.single)).mkString(" + \", \" + ")
        "return \"" + c.name + "(\" + " + parametersString + " + \")\";"
      }
      method("public", "String", "toString", "", content)
    }

  def writeDefinitions(ds: Iterable[Definition])(writeDefinition: Definition => Unit): Unit = {
    val (_, duplicates) =
      ((Set[String](), Set[String]()) /: ds.map(_.name)) {
        case ((nameSet, duplicates), name) =>
          if (nameSet.contains(name)) (nameSet, duplicates + name) else (nameSet + name, duplicates)
      }
    if (duplicates.isEmpty)
      ds.foreach(writeDefinition)
    else
      sys.error("Duplicate names:\n\t" + duplicates.mkString("\n\t"))
  }
  def implName(name: String) = name + "0"
}
