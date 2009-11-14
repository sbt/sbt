package xsbt.api

sealed trait Line extends NotNull { val line: Int }
final class ClassLine(val name: String, val level: Int, val line: Int) extends Line
final class EnumLine(val name: String, val members: Seq[String], val line: Int) extends Line
final class MemberLine(val name: String, val tpe: String, val single: Boolean, val level: Int, val line: Int) extends Line
final class WhitespaceLine(val comment: String, val line: Int) extends Line
