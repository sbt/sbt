/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbt.api

sealed trait Definition extends NotNull
{
	val name: String
}
final class ClassDef(val name: String, val parent: Option[ClassDef], val members: Seq[MemberDef]) extends Definition
{
	def allMembers = members ++ inheritedMembers
	def inheritedMembers: Seq[MemberDef] = parent.toList.flatMap(_.allMembers)
	def + (m: MemberLine) = new ClassDef(name, parent, members ++ Seq(new MemberDef(m.name, m.tpe.stripPrefix("~"), m.single, m.tpe.startsWith("~"))) )
}
final class EnumDef(val name: String, val members: Seq[String]) extends Definition

final class MemberDef(val name: String, val tpe: String, val single: Boolean, val lzy: Boolean) extends NotNull
{
	def javaType(accessor: Boolean) =
	{
		val base = tpe + (if(single) "" else "[]")
		if(!accessor && lzy) "Lazy<" + base + ">" else base
	}
	def scalaType(accessor: Boolean) =
	{
		val base = if(single) tpe else "Array[" + tpe + "]"
		if(!accessor && lzy) "Lazy[" + base + "]" else base
	}
	def asGet = name + (if(lzy) ".get()" else "")
	def asScalaDeclaration(accessor: Boolean) = name + ": " + scalaType(accessor)
	def asJavaDeclaration(accessor: Boolean) = javaType(accessor) + " " + name
	def mapType(f: String => String) = new MemberDef(name, f(tpe), single, lzy)
}