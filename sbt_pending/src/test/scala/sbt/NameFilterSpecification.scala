/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah */

package sbt

import org.scalacheck._
import Prop._

object NameFilterSpecification extends Properties("NameFilter")
{
	specify("All pass accepts everything", (s: String) => AllPassFilter.accept(s))
	specify("Exact filter matches provided string",
		(s1: String, s2: String) =>  (new ExactFilter(s1)).accept(s2) == (s1 == s2) )
	specify("Exact filter matches valid string", (s: String) => (new ExactFilter(s)).accept(s) )
	
	specify("Glob filter matches provided string if no *s",
		(s1: String, s2: String) =>
		{
			val stripped = stripAsterisksAndControl(s1)
			(GlobFilter(stripped).accept(s2) == (stripped == s2))
		})
	specify("Glob filter matches valid string if no *s",
		(s: String) =>
		{
			val stripped = stripAsterisksAndControl(s)
			GlobFilter(stripped).accept(stripped)
		})
	
	specify("Glob filter matches valid",
		(list: List[String]) =>
		{
			val stripped = list.map(stripAsterisksAndControl)
			GlobFilter(stripped.mkString("*")).accept(stripped.mkString)
		})
	
	/** Raw control characters are stripped because they are not allowed in expressions.
	* Asterisks are stripped because they are added under the control of the tests.*/
	private def stripAsterisksAndControl(s: String) = s.filter(c => !java.lang.Character.isISOControl(c) && c != '*').toString
}