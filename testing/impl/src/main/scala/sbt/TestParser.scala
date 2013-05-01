/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

/* The following implements the simple syntax for storing test definitions.
* The syntax is:
*
* definition := isModule? className typeSpecific
* isModule := '<module>'
* typeSpecific := annotated | subclass
* 
* subclass := subclassSeparator className
* subclassSeparator := '<<'
*
* annotated := annotationSeparator annotationName
* annotationSeparator := '@'
*/

	import scala.util.parsing.combinator._
	import testing.{Fingerprint, AnnotatedFingerprint, TestFingerprint}
	import DiscoveredParser._

sealed abstract class Discovered extends Fingerprint with NotNull
{
	/** Whether a test is a module or a class*/
	def isModule: Boolean
	def className: String
	// for TestFingerprint
	def testClassName = className
	def toDefinition: TestDefinition = new TestDefinition(className, this)
}
/** Represents a class 'className' that has 'superClassName' as an ancestor.*/
final case class DiscoveredSubclass(isModule: Boolean, className: String, superClassName: String) extends Discovered with TestFingerprint
{
	override def toString =
		(if(isModule) IsModuleLiteral else "") + className + SubSuperSeparator + superClassName
}
/** Represents an annotation on a method or class.*/
final case class DiscoveredAnnotated(isModule: Boolean, className: String, annotationName: String) extends Discovered with AnnotatedFingerprint
{
	override def toString =
		(if(isModule) IsModuleLiteral else "") + className + AnnotationSeparator + annotationName
}
final class DiscoveredParser extends RegexParsers with NotNull
{
	def definition: Parser[Discovered] =
		( isModule ~! className ~! (SubSuperSeparator | AnnotationSeparator) ~! className ) ^^ {
			case module ~ name ~ SubSuperSeparator ~ superName => DiscoveredSubclass(module, name.trim, superName.trim)
			case module ~ name ~ AnnotationSeparator ~ annotation => DiscoveredAnnotated(module, name.trim, annotation.trim)
		}

	def isModule: Parser[Boolean] = (IsModuleLiteral?) ^^ (_.isDefined)
	def className: Parser[String] = ClassNameRegexString.r
	
	def parse(definitionString: String): Either[String, Discovered] =
	{
		def parseError(msg: String) = Left("Could not parse discovered definition '" + definitionString + "': " + msg)
		parseAll(definition, definitionString) match
		{
			case Success(result, next) => Right(result)
			case err: NoSuccess => parseError(err.msg)
		}
	}
}
object DiscoveredParser
{
	val IsModuleLiteral = "<module>"
	val SubSuperSeparator = "<<"
	val AnnotationSeparator = "@"
	val ClassNameRegexString = """[^@<]+"""
	def parse(definitionString: String): Either[String, Discovered] = (new DiscoveredParser).parse(definitionString)
}