package xsbt.api

sealed trait Definition extends NotNull
{
	val name: String
}
final class ClassDef(val name: String, val parent: Option[ClassDef], val members: Seq[MemberDef]) extends Definition
{
	def allMembers = members ++ inheritedMembers
	def inheritedMembers: Seq[MemberDef] = parent.toList.flatMap(_.allMembers)
	def + (m: MemberLine) = new ClassDef(name, parent, members ++ Seq(new MemberDef(m.name, m.tpe, m.single)) )
}
final class EnumDef(val name: String, val members: Seq[String]) extends Definition

final class MemberDef(val name: String, val tpe: String, val single: Boolean) extends NotNull
{
	def javaType = tpe + (if(single) "" else "[]")
	def scalaType = if(single) tpe else "Array[" + tpe + "]"
	def asScalaDeclaration = name + ": " + scalaType
	def asJavaDeclaration = javaType + " " + name
	def mapType(f: String => String) = new MemberDef(name, f(tpe), single)
}