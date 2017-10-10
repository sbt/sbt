/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import Def.{ displayFull, displayMasked, ScopedKey }
import sbt.internal.{ TestBuild, Resolve }
import TestBuild._
import sbt.internal.util.complete._

import org.scalacheck._
import Gen._
import Prop._
import Arbitrary.arbBool

/**
 * Tests that the scoped key parser in Act can correctly parse a ScopedKey converted by Def.show*Key.
 * This includes properly resolving omitted components.
 */
object ParseKey extends Properties("Key parser test") {
  final val MaxKeys = 5
  final val MaxScopedKeys = 100

  implicit val gstructure = genStructure

  property("An explicitly specified axis is always parsed to that explicit value") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key, mask => mask0 }

      val hasZeroConfig = key.scope.config == Zero
      val mask = if (hasZeroConfig) mask0.copy(project = true) else mask0
      val expected = resolve(structure, key, mask)
      // Note that this explicitly displays the configuration axis set to Zero.
      // This is to disambiguate `proj/Zero/name`, which could render potentially
      // as `Zero/name`, but could be interpretted as `Zero/Zero/name`.
      val s = displayMasked(key, mask, hasZeroConfig)
      ("Key: " + displayPedantic(key)) |:
        parseExpected(structure, s, expected, mask)
    }

  property("An unspecified project axis resolves to the current project") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key }

      val mask = skm.mask.copy(project = false)
      val string = displayMasked(key, mask)
      // skip when config axis is set to Zero
      val hasZeroConfig = key.scope.config == Zero

      ("Key: " + displayPedantic(key)) |:
        ("Mask: " + mask) |:
        ("Current: " + structure.current) |:
        parse(structure, string) {
        case Left(err)                  => false
        case Right(sk) if hasZeroConfig => true
        case Right(sk)                  => sk.scope.project == Select(structure.current)
      }
    }

  property("An unspecified task axis resolves to Zero") = forAllNoShrink(structureDefinedKey) {
    (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = skm.mask.copy(task = false)
      val string = displayMasked(key, mask)

      ("Key: " + displayPedantic(key)) |:
        ("Mask: " + mask) |:
        parse(structure, string) {
        case Left(err) => false
        case Right(sk) => sk.scope.task == Zero
      }
  }

  property(
    "An unspecified configuration axis resolves to the first configuration directly defining the key or else Zero") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = ScopeMask(config = false)
      val string = displayMasked(key, mask)
      val resolvedConfig = Resolve.resolveConfig(structure.extra, key.key, mask)(key.scope).config

      ("Key: " + displayPedantic(key)) |:
        ("Mask: " + mask) |:
        ("Expected configuration: " + resolvedConfig.map(_.name)) |:
        parse(structure, string) {
        case Right(sk) => (sk.scope.config == resolvedConfig) || (sk.scope == Scope.GlobalScope)
        case Left(err) => false
      }
    }

  def displayPedantic(scoped: ScopedKey[_]): String =
    Scope.displayPedantic(scoped.scope, scoped.key.label)

  lazy val structureDefinedKey: Gen[StructureKeyMask] = structureKeyMask { s =>
    for (scope <- TestBuild.scope(s.env); key <- oneOf(s.allAttributeKeys.toSeq))
      yield ScopedKey(scope, key)
  }
  def structureKeyMask(genKey: Structure => Gen[ScopedKey[_]])(
      implicit maskGen: Gen[ScopeMask],
      structureGen: Gen[Structure]): Gen[StructureKeyMask] =
    for (mask <- maskGen; structure <- structureGen; key <- genKey(structure))
      yield new StructureKeyMask(structure, key, mask)
  final class StructureKeyMask(val structure: Structure, val key: ScopedKey[_], val mask: ScopeMask)

  def resolve(structure: Structure, key: ScopedKey[_], mask: ScopeMask): ScopedKey[_] =
    ScopedKey(Resolve(structure.extra, Select(structure.current), key.key, mask)(key.scope),
              key.key)

  def parseExpected(structure: Structure,
                    s: String,
                    expected: ScopedKey[_],
                    mask: ScopeMask): Prop =
    ("Expected: " + displayFull(expected)) |:
      ("Mask: " + mask) |:
      parse(structure, s) {
      case Left(err) => false
      case Right(sk) =>
        (s"${sk}.key == ${expected}.key: ${sk.key == expected.key}") |:
          (s"${sk.scope} == ${expected.scope}: ${Scope.equal(sk.scope, expected.scope, mask)}") |:
          Project.equal(sk, expected, mask)
    }

  def parse(structure: Structure, s: String)(f: Either[String, ScopedKey[_]] => Prop): Prop = {
    val parser = makeParser(structure)
    val parsed = DefaultParsers.result(parser, s).left.map(_().toString)
    val showParsed = parsed.right.map(displayFull)
    ("Key string: '" + s + "'") |:
      ("Parsed: " + showParsed) |:
      ("Structure: " + structure) |:
      f(parsed)
  }

  // Here we're shadowing the in-scope implicit called `mkEnv` for this method
  // so that it will use the passed-in `Gen` rather than the one imported
  // from TestBuild.
  def genStructure(implicit mkEnv: Gen[Env]): Gen[Structure] =
    structureGenF { (scopes: Seq[Scope], env: Env, current: ProjectRef) =>
      val settings =
        for {
          scope <- scopes
          t <- env.tasks
        } yield Def.setting(ScopedKey(scope, t.key), Def.value(""))
      TestBuild.structure(env, settings, current)
    }

  // Here we're shadowing the in-scope implicit called `mkEnv` for this method
  // so that it will use the passed-in `Gen` rather than the one imported
  // from TestBuild.
  def structureGenF(f: (Seq[Scope], Env, ProjectRef) => Structure)(
      implicit mkEnv: Gen[Env]): Gen[Structure] =
    structureGen((s, e, p) => Gen.const(f(s, e, p)))
  // Here we're shadowing the in-scope implicit called `mkEnv` for this method
  // so that it will use the passed-in `Gen` rather than the one imported
  // from TestBuild.
  def structureGen(f: (Seq[Scope], Env, ProjectRef) => Gen[Structure])(
      implicit mkEnv: Gen[Env]): Gen[Structure] =
    for {
      env <- mkEnv
      loadFactor <- choose(0.0, 1.0)
      scopes <- pickN(loadFactor, env.allFullScopes)
      current <- oneOf(env.allProjects.unzip._1)
      structure <- f(scopes, env, current)
    } yield structure

  // pickN is a function that randomly picks load % items from the from sequence.
  // The rest of the tests expect at least one item, so I changed it to return 1 in case of 0.
  def pickN[T](load: Double, from: Seq[T]): Gen[Seq[T]] =
    pick(Math.max((load * from.size).toInt, 1), from)
}
