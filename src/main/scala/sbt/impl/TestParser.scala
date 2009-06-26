/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

/* The following implements the simple syntax for storing test definitions.
* The syntax is:
*
* definition := isModule? className separator className
* isModule := '<module>'
* separator := '<<'
*/

import scala.util.parsing.combinator._

import TestParser._
/** Represents a test implemented by 'testClassName' of type 'superClassName'.*/
final case class TestDefinition(isModule: Boolean, testClassName: String, superClassName: String) extends NotNull
{
	override def toString =
		(if(isModule) IsModuleLiteral else "") + testClassName + SubSuperSeparator + superClassName
}
final class TestParser extends RegexParsers with NotNull
{
	def test: Parser[TestDefinition] =
		( isModule ~! className ~! SubSuperSeparator ~! className ) ^^
			{ case module ~ testName ~ SubSuperSeparator ~ superName => TestDefinition(module, testName.trim, superName.trim) }
	def isModule: Parser[Boolean] = (IsModuleLiteral?) ^^ (_.isDefined)
	def className: Parser[String] = ClassNameRegexString.r
	
	def parse(testDefinitionString: String): Either[String, TestDefinition] =
	{
		def parseError(msg: String) = Left("Could not parse test definition '" + testDefinitionString + "': " + msg)
		parseAll(test, testDefinitionString) match
		{
			case Success(result, next) => Right(result)
			case err: NoSuccess => parseError(err.msg)
		}
	}
}
object TestParser
{
	val IsModuleLiteral = "<module>"
	val SubSuperSeparator = "<<"
	val ClassNameRegexString = """[^<]+"""
	def parse(testDefinitionString: String): Either[String, TestDefinition] = (new TestParser).parse(testDefinitionString)
}