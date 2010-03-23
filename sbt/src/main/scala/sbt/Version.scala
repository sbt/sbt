/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

sealed trait Version extends NotNull
case class BasicVersion(major: Int, minor: Option[Int], micro: Option[Int], extra: Option[String]) extends Version
{
	import Version._
	require(major >= 0, "Major revision must be nonnegative.")
	require(minor.isDefined || micro.isEmpty, "Cannot define micro revision without defining minor revision.")
	requirePositive(minor)
	requirePositive(micro)
	require(isValidExtra(extra))
	
	def incrementMicro = BasicVersion(major, minor orElse Some(0), increment(micro), extra)
	def incrementMinor = BasicVersion(major, increment(minor), micro, extra)
	def incrementMajor = BasicVersion(major+1, minor, micro, extra)
	def withExtra(newExtra: Option[String]) = BasicVersion(major, minor, micro, newExtra)
	
	override def toString = major +
		minor.map(minorI => "." + minorI + micro.map(microI => "." + microI).getOrElse("")).getOrElse("") +
			extra.map(x => "-" + x).getOrElse("")
}
case class OpaqueVersion(value: String) extends Version
{
	require(!value.trim.isEmpty)
	override def toString = value
}
object Version
{
	private[sbt] def increment(i: Option[Int]) = Some(i.getOrElse(0) + 1)
	private[sbt] def requirePositive(i: Option[Int]) { i.foreach(x => require(x >= 0)) }
	
	import java.util.regex.Pattern
	val versionPattern = Pattern.compile("""(\d+)(?:\.(\d+)(?:\.(\d+))?)?(?:-(.+))?""")
	def fromString(v: String): Either[String, Version] =
	{
		val trimmed = v.trim
		if(trimmed.isEmpty)
			Left("Version cannot be empty.")
		else
		{
			val matcher = versionPattern.matcher(trimmed)
			import matcher._
			if(matches)
			{
				def toOption(index: Int) =
				{
					val v = group(index)
					if(v == null) None else Some(v)
				}
				def toInt(index: Int) = toOption(index).map(_.toInt)
				val extra = toOption(4)
				if(isValidExtra(extra))
					Right(BasicVersion(group(1).toInt, toInt(2), toInt(3), extra))
				else
					Right(OpaqueVersion(trimmed))
			}
			else
				Right(OpaqueVersion(trimmed))
		}
	}
	def isValidExtra(e: Option[String]): Boolean = e.map(isValidExtra).getOrElse(true)
	def isValidExtra(s: String): Boolean = !(s.trim.isEmpty || s.exists(java.lang.Character.isISOControl))
}
