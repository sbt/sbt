package sbt

	import java.util.regex.Pattern
	import scala.Console.{BOLD, RESET}

object Highlight
{
	final val NormalIntensity = "\033[22m"
	final val NormalTextColor = "\033[39m"

	def showMatches(pattern: Pattern)(line: String): Option[String] =
	{
		val matcher = pattern.matcher(line)
		if(ConsoleLogger.formatEnabled)
		{
			val highlighted = matcher.replaceAll(scala.Console.RED + "$0" + NormalTextColor)
			if(highlighted == line) None else Some(highlighted)
		}
		else if(matcher.find)
			Some(line)
		else
			None
	}
	def bold(s: String) = if(ConsoleLogger.formatEnabled) BOLD + s + NormalIntensity else s
}