/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import Def.{ displayFull, displayMasked, ScopedKey }
import sbt.internal.{ TestBuild, Resolve }, TestBuild._
import sbt.internal.util.complete.Parser

import org.scalacheck._, Arbitrary.arbitrary, Gen._, Prop._

/**
 * Tests that the scoped key parser in Act can correctly parse a ScopedKey converted by Def.show*Key.
 * This includes properly resolving omitted components.
 */
object ParseKey extends Properties("Key parser test") {
  property("An explicitly specified axis is always parsed to that explicit value") = forAll {
    (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val hasZeroConfig = key.scope.config == Zero
      val mask = if (hasZeroConfig) skm.mask.copy(project = true) else skm.mask
      // Note that this explicitly displays the configuration axis set to Zero.
      // This is to disambiguate `proj/Zero/name`, which could render potentially
      // as `Zero/name`, but could be interpreted as `Zero/Zero/name`.
      val expected = ScopedKey(
        Resolve(structure.extra, Select(structure.current), key.key, mask)(key.scope),
        key.key
      )
      parseCheck(structure, key, mask, hasZeroConfig)(
        sk =>
          Project.equal(sk, expected, mask)
            :| s"$sk.key == $expected.key: ${sk.key == expected.key}"
            :| s"${sk.scope} == ${expected.scope}: ${Scope.equal(sk.scope, expected.scope, mask)}"
      ) :| s"Expected: ${displayFull(expected)}"
  }

  property("An unspecified project axis resolves to the current project") = forAll {
    (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = skm.mask.copy(project = false)
      // skip when config axis is set to Zero
      val hasZeroConfig = key.scope.config == Zero
      parseCheck(structure, key, mask)(
        sk =>
          (hasZeroConfig || sk.scope.project == Select(structure.current))
            :| s"Current: ${structure.current}"
      )
  }

  property("An unspecified task axis resolves to Zero") = forAll { (skm: StructureKeyMask) =>
    import skm.{ structure, key }
    val mask = skm.mask.copy(task = false)
    parseCheck(structure, key, mask)(_.scope.task == Zero)
  }

  property(
    "An unspecified configuration axis resolves to the first configuration directly defining the key or else Zero") =
    forAll { (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = ScopeMask(config = false)
      val resolvedConfig = Resolve.resolveConfig(structure.extra, key.key, mask)(key.scope).config
      parseCheck(structure, key, mask)(
        sk => (sk.scope.config == resolvedConfig) || (sk.scope == Scope.GlobalScope)
      ) :| s"Expected configuration: ${resolvedConfig map (_.name)}"
    }

  implicit val arbStructure: Arbitrary[Structure] = Arbitrary {
    for {
      env <- mkEnv
      loadFactor <- choose(0.0, 1.0)
      scopes <- pickN(loadFactor, env.allFullScopes)
      current <- oneOf(env.allProjects.unzip._1)
      structure <- {
        val settings = for (scope <- scopes; t <- env.tasks)
          yield Def.setting(ScopedKey(scope, t.key), Def.value(""))
        TestBuild.structure(env, settings, current)
      }
    } yield structure
  }

  final class StructureKeyMask(val structure: Structure, val key: ScopedKey[_], val mask: ScopeMask)

  implicit val arbStructureKeyMask: Arbitrary[StructureKeyMask] = Arbitrary {
    for {
      mask <- maskGen
      structure <- arbitrary[Structure]
      key <- for {
        scope <- TestBuild.scope(structure.env)
        key <- oneOf(structure.allAttributeKeys.toSeq)
      } yield ScopedKey(scope, key)
    } yield new StructureKeyMask(structure, key, mask)
  }

  def parseCheck(
      structure: Structure,
      key: ScopedKey[_],
      mask: ScopeMask,
      showZeroConfig: Boolean = false,
  )(f: ScopedKey[_] => Prop): Prop = {
    val s = displayMasked(key, mask, showZeroConfig)
    val parser = makeParser(structure)
    val parsed = Parser.result(parser, s).left.map(_().toString)
    (
      parsed.fold(_ => falsified, f)
        :| s"Key: ${Scope.displayPedantic(key.scope, key.key.label)}"
        :| s"Mask: $mask"
        :| s"Key string: '$s'"
        :| s"Parsed: ${parsed.right.map(displayFull)}"
        :| s"Structure: $structure"
    )
  }

  // pickN is a function that randomly picks load % items from the "from" sequence.
  // The rest of the tests expect at least one item, so I changed it to return 1 in case of 0.
  def pickN[T](load: Double, from: Seq[T]): Gen[Seq[T]] =
    pick((load * from.size).toInt max 1, from)
}
