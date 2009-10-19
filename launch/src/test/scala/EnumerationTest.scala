package xsbt.boot

import org.scalacheck._
import Prop.{Exception => _,_}

object EnumerationTest extends Properties("Enumeration")
{
	property("MultiEnum.toValue") = checkToValue(MultiEnum, multiElements : _*)
	property("MultiEnum.elements") = checkElements(MultiEnum, multiElements : _*)
	property("EmptyEnum.toValue") = checkToValue(EmptyEnum)
	property("EmptyEnum.elements") = EmptyEnum.elements.isEmpty
	property("SingleEnum.toValue") = checkToValue( SingleEnum, singleElements )
	property("SingleEnum.elements") = checkElements( SingleEnum,singleElements )

	def singleElements = ("A", SingleEnum.a)
	def multiElements =
	{
		import MultiEnum.{a,b,c}
		 List(("A" -> a), ("B" -> b), ("C" -> c))
	}

	def checkElements(enum: Enumeration, mapped: (String, Enumeration#Value)*) =
	{
		val elements = enum.elements
		("elements: " + elements) |:
			( mapped.forall{ case (s,v) => elements.contains(v) } && (elements.length == mapped.length) )
	}
	def checkToValue(enum: Enumeration, mapped: (String, Enumeration#Value)*) =
	{
		def invalid(s: String) =
			("valueOf(" + s + ")") |:
				Prop.throws(enum.toValue(s), classOf[Exception])
		def valid(s: String, expected: Enumeration#Value) =
			("valueOf(" + s + ")") |:
			("Expected " + expected) |:
				( enum.toValue(s) == expected )
		val map = Map( mapped : _*)
		Prop.forAll( (s: String) =>
			map.get(s) match {
				case Some(v) => valid(s, v)
				case None => invalid(s)
			} )
	}
	object MultiEnum extends Enumeration
	{
		val a = value("A")
		val b = value("B")
		val c = value("C")
	}
	object SingleEnum extends Enumeration
	{
		val a = value("A")
	}
	object EmptyEnum extends Enumeration
}