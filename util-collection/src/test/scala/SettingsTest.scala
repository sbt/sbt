/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import hedgehog.*
import hedgehog.runner.*

object SettingsTest extends Properties:
  val settingsUsage = SettingsUsage(SettingsExample())
  val settingsExample: settingsUsage.settingsExample.type = settingsUsage.settingsExample
  import settingsExample.*
  import settingsUsage.{ a, applied, b }

  override def tests: List[Test] = List(
    example("SettingsUsage evaluates to the documented values", testUsage),
    property("a chain of maps evaluates to its length", propChain),
    property("a chain of binds evaluates to its base", propBindChain),
    property("a bind may refer back to a completed setting", propAllowedReference),
    property(
      "a derived setting chain depends on the previous derived and a normal setting",
      propDerivedChain
    ),
    example(
      "derived settings replace the DerivedSetting in the Seq[Setting[?]]",
      testDerivedKeepsPosition
    ),
    property(
      "a DerivedSetting in a global scope is scoped under each project, allowing safe +=",
      propDerivedSettingsScope
    ),
  )

  val expectedValues: List[Option[Int]] =
    None :: None :: None :: None :: None :: None :: Some(3) :: None ::
      Some(3) :: Some(9) :: Some(4) :: Some(9) :: Nil

  val ch = AttributeKey[Int]("ch")
  val chk = ScopedKey(Scope(0), ch)

  def testUsage: Result =
    Result.all(
      (for
        i <- 0 to 5
        k <- Seq(a, b)
      yield
        val expected = expectedValues(2 * i + (if k == a then 0 else 1))
        checkKey[Int](ScopedKey(Scope(i), k), expected, applied)
      ).toList
    )

  def chain(i: Int, prev: Initialize[Int]): Initialize[Int] =
    if i <= 0 then prev else chain(i - 1, prev(_ + 1))

  def chainBind(prev: Initialize[Int]): Initialize[Int] =
    flatMap(prev): v =>
      if v <= 0 then prev else chainBind(value(v - 1))

  def propChain: Property =
    for n <- Gen.int(Range.linear(1, 5000)).forAll
    yield singleIntTest(chain(n, value(0)), n)

  def propBindChain: Property =
    for n <- Gen.int(Range.linear(1, 5000)).forAll
    yield singleIntTest(chainBind(value(n)), 0)

  def propAllowedReference: Property =
    for intermediate <- Gen.int(Range.linear(0, 30)).forAll
    yield
      val top = value(intermediate)
      def iterate(init: Initialize[Int]): Initialize[Int] =
        flatMap(init): t =>
          if t <= 0 then top else iterate(value(t - 1))
      checkKey(chk, Some(intermediate), evaluate(setting(chk, iterate(top)) :: Nil))

  def propDerivedChain: Property =
    for nr <- Gen.int(Range.linear(1, 100)).forAll
    yield
      val scopedKeys = (0 until nr).toList.map(i => ScopedKey(Scope(0), AttributeKey[Int](s"k$i")))
      val last = scopedKeys.last
      val derivedSettings: Seq[Setting[Int]] =
        (chk :: scopedKeys)
          .sliding(2)
          .collect:
            case List(scoped0, scoped1) =>
              val nextInit =
                if scoped0 == chk then chk else scoped0.zipWith(chk)((p, _) => p + 1)
              derive(setting(scoped1, nextInit))
          .toSeq
      Result.all(
        List(
          checkKey(last, Some(nr - 1), evaluate(setting(chk, value(0)) +: derivedSettings))
            .log("not derived"),
          checkKey(last, None, evaluate(derivedSettings)).log("should not be derived"),
        )
      )

  def testDerivedKeepsPosition: Result =
    val a: ScopedKey[Int] = ScopedKey(Scope(0), AttributeKey[Int]("a"))
    val b: ScopedKey[Int] = ScopedKey(Scope(0), AttributeKey[Int]("b"))
    val ev1 = evaluate(
      Seq(
        setting(a, value(3)),
        setting(b, value(6)),
        derive(setting(b, a)),
        setting(a, value(5)),
        setting(b, value(8))
      )
    )
    val ev2 = evaluate(
      Seq(
        setting(a, value(3)),
        setting(b, value(6)),
        derive(setting(b, a)),
        setting(a, value(5))
      )
    )
    Result.all(
      List(
        checkKey(a, Some(5), ev1),
        checkKey(b, Some(8), ev1),
        checkKey(a, Some(5), ev2),
        checkKey(b, Some(5), ev2),
      )
    )
  end testDerivedKeepsPosition

  def propDerivedSettingsScope: Property =
    for nrProjects <- Gen.int(Range.linear(1, 100)).forAll
    yield
      val key = AttributeKey[Int]("key")
      val derivedKey = AttributeKey[Int]("derivedKey")
      val projectKeys = (1 to nrProjects).map(proj => ScopedKey(Scope(1, proj), key))
      val projectDerivedKeys = (1 to nrProjects).map(proj => ScopedKey(Scope(1, proj), derivedKey))
      val globalKey = ScopedKey(Scope(0), key)
      val globalDerivedKey = ScopedKey(Scope(0), derivedKey)
      val settings: Seq[Setting[?]] =
        derive(setting(globalDerivedKey, globalKey(_ + 1))) +:
          projectKeys.map(pk => setting(pk, value(0)))
      val ev = evaluate(settings)
      Result.all(
        checkKey(globalDerivedKey, None, ev) ::
          projectDerivedKeys.toList.map(pk => checkKey(pk, Some(1), ev))
      )

  def singleIntTest(i: Initialize[Int], expected: Int): Result =
    checkKey(chk, Some(expected), evaluate(setting(chk, i) :: Nil))

  def checkKey[A](key: ScopedKey[A], expected: Option[A], settings: Settings): Result =
    (settings.get(key) ==== expected).log(s"Key: $key")

  def evaluate(settings: Seq[Setting[?]]): Settings =
    makeWithCompiledMap(settings)(using delegates, scopeLocal, showFullKey)._2
end SettingsTest
