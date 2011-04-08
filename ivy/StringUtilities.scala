/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package sbt

object StringUtilities
{
	def normalize(s: String) = s.toLowerCase.replaceAll("""\s+""", "-")
	def nonEmpty(s: String, label: String)
	{
		require(s.trim.length > 0, label + " cannot be empty.")
	}
	def appendable(s: String) = if(s.isEmpty) "" else "_" + s
}
