package sbt

import org.scalacheck._
import Prop._
import SettingsUsage._
import SettingsExample._

object SettingsTest extends Properties("settings")
{
	final val ChainMax = 5000
	lazy val chainLengthGen = Gen.choose(1, ChainMax)

	property("Basic settings test") = secure( all( tests: _*) )

	property("Basic chain") = forAll(chainLengthGen) { (i: Int) =>
		val abs = math.abs(i)
		singleIntTest( chain( abs, value(0)), abs )
	}


	def tests =
		for(i <- 0 to 5; k <- Seq(a, b)) yield {
			val expected = expectedValues(2*i + (if(k == a) 0 else 1))
			checkKey[Int]( ScopedKey( Scope(i), k ), expected, applied)
		}

	lazy val expectedValues = None :: None :: None :: None :: None :: None :: Some(3) :: None :: Some(3) :: Some(9) :: Some(4) :: Some(9) :: Nil

	lazy val ch = AttributeKey[Int]("ch")
	lazy val chk = ScopedKey( Scope(0), ch)
	def chain(i: Int, prev: Initialize[Int]): Initialize[Int] =
		if(i <= 0) prev else chain(i - 1, prev(_ + 1))

	def singleIntTest(i: Initialize[Int], expected: Int) =
	{
		val eval = evaluate( setting( chk, i ) :: Nil )
		checkKey( chk, Some(expected), eval )
	}

	def checkKey[T](key: ScopedKey[T], expected: Option[T], settings: Settings[Scope]) =
	{
		val value = settings.get( key.scope, key.key)
		("Key: " + key) |:
		("Value: " + value) |:
		("Expected: " + expected) |:
		(value == expected)
	}

	def evaluate(settings: Seq[Setting[_]]): Settings[Scope] =
		try { make(settings)(delegates, scopeLocal, showFullKey) }
		catch { case e: Throwable => e.printStackTrace; throw e }
}
