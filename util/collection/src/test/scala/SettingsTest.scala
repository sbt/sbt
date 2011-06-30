package sbt

import org.scalacheck._
import Prop._
import SettingsUsage._

object SettingsTest extends Properties("settings")
{
	def tests = 
		for(i <- 0 to 5; k <- Seq(a, b)) yield {
			val value = applied.get( Scope(i), k)
			val expected = expectedValues(2*i + (if(k == a) 0 else 1))
			("Index: " + i) |:
			("Key: " + k.label) |:
			("Value: " + value) |:
			("Expected: " + expected) |:
			(value == expected)
		}

	property("Basic settings test") = secure( all( tests: _*) )

	lazy val expectedValues = None :: None :: None :: None :: None :: None :: Some(3) :: None :: Some(3) :: Some(9) :: Some(4) :: Some(9) :: Nil
}