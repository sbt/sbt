/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import org.scalacheck._
import Prop._

object SettingsTest extends Properties("settings") {
  val settingsExample: SettingsExample = SettingsExample()
  import settingsExample._
  val settingsUsage = SettingsUsage(settingsExample)
  import settingsUsage._

  import scala.reflect.Manifest

  final val ChainMax = 5000
  lazy val chainLengthGen = Gen.choose(1, ChainMax)

  property("Basic settings test") = secure(all(tests: _*))

  property("Basic chain") = forAll(chainLengthGen) { (i: Int) =>
    val abs = math.abs(i)
    singleIntTest(chain(abs, value(0)), abs)
  }
  property("Basic bind chain") = forAll(chainLengthGen) { (i: Int) =>
    val abs = math.abs(i)
    singleIntTest(chainBind(value(abs)), 0)
  }

  property("Allows references to completed settings") = forAllNoShrink(30) { allowedReference }
  final def allowedReference(intermediate: Int): Prop = {
    val top = value(intermediate)
    def iterate(init: Initialize[Int]): Initialize[Int] =
      bind(init) { t =>
        if (t <= 0)
          top
        else
          iterate(value(t - 1))
      }
    evaluate(setting(chk, iterate(top)) :: Nil); true
  }

  property("Derived setting chain depending on (prev derived, normal setting)") =
    forAllNoShrink(Gen.choose(1, 100).label("numSettings")) { derivedSettings }
  final def derivedSettings(nr: Int): Prop = {
    val genScopedKeys = {
      // We wan
      // t to generate lists of keys that DO NOT inclue the "ch" key we use to check things.
      val attrKeys = mkAttrKeys[Int](nr).filter(_.forall(_.label != "ch"))
      attrKeys map (_ map (ak => ScopedKey(Scope(0), ak)))
    }.label("scopedKeys").filter(_.nonEmpty)
    forAll(genScopedKeys) { scopedKeys =>
      try {
        // Note; It's evil to grab last IF you haven't verified the set can't be empty.
        val last = scopedKeys.last
        val derivedSettings: Seq[Setting[Int]] = (
          for {
            List(scoped0, scoped1) <- chk :: scopedKeys sliding 2
            nextInit = if (scoped0 == chk) chk
            else
              (scoped0 zipWith chk) { (p, _) =>
                p + 1
              }
          } yield derive(setting(scoped1, nextInit))
        ).toSeq

        {
          // Note: This causes a cycle refernec error, quite frequently.
          checkKey(last, Some(nr - 1), evaluate(setting(chk, value(0)) +: derivedSettings)) :| "Not derived?"
        } && {
          checkKey(last, None, evaluate(derivedSettings)) :| "Should not be derived"
        }
      } catch {
        case t: Throwable =>
          // TODO - For debugging only.
          t.printStackTrace(System.err)
          throw t
      }
    }
  }

  private def mkAttrKeys[T](nr: Int)(implicit mf: Manifest[T]): Gen[List[AttributeKey[T]]] = {
    import Gen._
    val nonEmptyAlphaStr =
      nonEmptyListOf(alphaChar)
        .map({ xs: List[Char] =>
          val s = xs.mkString
          s.take(1).toLowerCase + s.drop(1)
        })
        .suchThat(_.forall(_.isLetter))

    (for {
      list <- Gen.listOfN(nr, nonEmptyAlphaStr) suchThat (l => l.size == l.distinct.size)
      item <- list
    } yield AttributeKey[T](item)).label(s"mkAttrKeys($nr)")
  }

  property("Derived setting(s) replace DerivedSetting in the Seq[Setting[_]]") =
    derivedKeepsPosition
  final def derivedKeepsPosition: Prop = {
    val a: ScopedKey[Int] = ScopedKey(Scope(0), AttributeKey[Int]("a"))
    val b: ScopedKey[Int] = ScopedKey(Scope(0), AttributeKey[Int]("b"))
    val prop1 = {
      val settings: Seq[Setting[_]] = Seq(
        setting(a, value(3)),
        setting(b, value(6)),
        derive(setting(b, a)),
        setting(a, value(5)),
        setting(b, value(8))
      )
      val ev = evaluate(settings)
      checkKey(a, Some(5), ev) && checkKey(b, Some(8), ev)
    }
    val prop2 = {
      val settings: Seq[Setting[Int]] = Seq(
        setting(a, value(3)),
        setting(b, value(6)),
        derive(setting(b, a)),
        setting(a, value(5))
      )
      val ev = evaluate(settings)
      checkKey(a, Some(5), ev) && checkKey(b, Some(5), ev)
    }
    prop1 && prop2
  }

  property(
    "DerivedSetting in ThisBuild scopes derived settings under projects thus allowing safe +="
  ) = forAllNoShrink(Gen.choose(1, 100)) { derivedSettingsScope }
  final def derivedSettingsScope(nrProjects: Int): Prop = {
    forAll(mkAttrKeys[Int](2)) {
      case List(key, derivedKey) =>
        val projectKeys = for { proj <- 1 to nrProjects } yield ScopedKey(Scope(1, proj), key)
        val projectDerivedKeys = for { proj <- 1 to nrProjects } yield
          ScopedKey(Scope(1, proj), derivedKey)
        val globalKey = ScopedKey(Scope(0), key)
        val globalDerivedKey = ScopedKey(Scope(0), derivedKey)
        // Each project defines an initial value, but the update is defined in globalKey.
        // However, the derived Settings that come from this should be scoped in each project.
        val settings: Seq[Setting[_]] =
          derive(setting(globalDerivedKey, settingsExample.map(globalKey)(_ + 1))) +: projectKeys
            .map(pk => setting(pk, value(0)))
        val ev = evaluate(settings)
        // Also check that the key has no value at the "global" scope
        val props = for { pk <- projectDerivedKeys } yield checkKey(pk, Some(1), ev)
        checkKey(globalDerivedKey, None, ev) && Prop.all(props: _*)
    }
  }

  // Circular (dynamic) references currently loop infinitely.
  //  This is the expected behavior (detecting dynamic cycles is expensive),
  //  but it may be necessary to provide an option to detect them (with a performance hit)
  // This would test that cycle detection.
  //  property("Catches circular references") = forAll(chainLengthGen) { checkCircularReferences _ }
  final def checkCircularReferences(intermediate: Int): Prop = {
    val ccr = new CCR(intermediate)
    try { evaluate(setting(chk, ccr.top) :: Nil); false } catch {
      case e: java.lang.Exception => true
    }
  }

  def tests =
    for (i <- 0 to 5; k <- Seq(a, b)) yield {
      val expected = expectedValues(2 * i + (if (k == a) 0 else 1))
      checkKey[Int](ScopedKey(Scope(i), k), expected, applied)
    }

  lazy val expectedValues = None :: None :: None :: None :: None :: None :: Some(3) :: None ::
    Some(3) :: Some(9) :: Some(4) :: Some(9) :: Nil

  lazy val ch = AttributeKey[Int]("ch")
  lazy val chk = ScopedKey(Scope(0), ch)
  def chain(i: Int, prev: Initialize[Int]): Initialize[Int] =
    if (i <= 0) prev else chain(i - 1, prev(_ + 1))

  def chainBind(prev: Initialize[Int]): Initialize[Int] =
    bind(prev) { v =>
      if (v <= 0) prev else chainBind(value(v - 1))
    }
  def singleIntTest(i: Initialize[Int], expected: Int) = {
    val eval = evaluate(setting(chk, i) :: Nil)
    checkKey(chk, Some(expected), eval)
  }

  def checkKey[T](key: ScopedKey[T], expected: Option[T], settings: Settings[Scope]) = {
    val value = settings.get(key.scope, key.key)
    ("Key: " + key) |:
      ("Value: " + value) |:
      ("Expected: " + expected) |:
      (value == expected)
  }

  def evaluate(settings: Seq[Setting[_]]): Settings[Scope] =
    try { make(settings)(delegates, scopeLocal, showFullKey) } catch {
      case e: Throwable => e.printStackTrace; throw e
    }
}
// This setup is a workaround for module synchronization issues
final class CCR(intermediate: Int) {
  import SettingsTest.settingsExample._
  lazy val top = iterate(value(intermediate), intermediate)
  def iterate(init: Initialize[Int], i: Int): Initialize[Int] =
    bind(init) { t =>
      if (t <= 0)
        top
      else
        iterate(value(t - 1), t - 1)
    }
}
