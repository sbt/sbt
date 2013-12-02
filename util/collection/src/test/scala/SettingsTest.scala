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
	property("Basic bind chain") = forAll(chainLengthGen) { (i: Int) =>
		val abs = math.abs(i)
		singleIntTest( chainBind(value(abs)), 0 )
	}

	property("Allows references to completed settings") = forAllNoShrink(30) { allowedReference }
	final def allowedReference(intermediate: Int): Prop =
	{
		val top = value(intermediate)
		def iterate(init: Initialize[Int]): Initialize[Int] =
			bind(init) { t =>
				if(t <= 0)
					top
				else
					iterate(value(t-1) )
			}
		evaluate( setting(chk, iterate(top)) :: Nil); true
	}

  property("Derived setting chain depending on (prev derived, normal setting)") = forAllNoShrink(Gen.choose(1, 100)) { derivedSettings }
  final def derivedSettings(nr: Int): Prop =
  {
    val alphaStr = Gen.alphaStr
    val genScopedKeys = {
      val attrKeys = for {
        list <- Gen.listOfN(nr, alphaStr) suchThat (l => l.size == l.distinct.size)
        item <- list
      } yield AttributeKey[Int](item)
      attrKeys map (_ map (ak => ScopedKey(Scope(0), ak)))
    }
    forAll(genScopedKeys) { scopedKeys =>
      val last = scopedKeys.last
      val derivedSettings: Seq[Setting[Int]] = (
        for {
          List(scoped0, scoped1) <- chk :: scopedKeys sliding 2
          nextInit = if (scoped0 == chk) chk
                     else (scoped0 zipWith chk) { (p, _) => p + 1 }
        } yield derive(setting(scoped1, nextInit))
      ).toSeq

      { checkKey(last, Some(nr-1), evaluate(setting(chk, value(0)) +: derivedSettings)) :| "Not derived?" } &&
      { checkKey( last, None, evaluate(derivedSettings)) :| "Should not be derived" }
    }
  }

// Circular (dynamic) references currently loop infinitely.
//  This is the expected behavior (detecting dynamic cycles is expensive),
//  but it may be necessary to provide an option to detect them (with a performance hit)
// This would test that cycle detection.
//	property("Catches circular references") = forAll(chainLengthGen) { checkCircularReferences _ }
	final def checkCircularReferences(intermediate: Int): Prop =
	{
		val ccr = new CCR(intermediate)
		try { evaluate( setting(chk, ccr.top) :: Nil); false }
		catch { case e: java.lang.Exception => true }
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

	def chainBind(prev: Initialize[Int]): Initialize[Int] =
		bind(prev) { v =>
			if(v <= 0) prev else chainBind(value(v - 1) )
		}
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
// This setup is a workaround for module synchronization issues 
final class CCR(intermediate: Int)
{
	lazy val top = iterate(value(intermediate), intermediate)
	def iterate(init: Initialize[Int], i: Int): Initialize[Int] =
		bind(init) { t =>
			if(t <= 0)
				top
			else
				iterate(value(t - 1), t-1)
		}
}
