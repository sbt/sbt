package sbt

import Def.{ displayFull, displayMasked, ScopedKey }
import java.net.URI
import TestBuild._
import complete._

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
      import skm.{ structure, key, mask }

      val expected = resolve(structure, key, mask)
      val string = displayMasked(key, mask)

      ("Key: " + displayFull(key)) |:
        parseExpected(structure, string, expected, mask)
    }

  property("An unspecified project axis resolves to the current project") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key }

      val mask = skm.mask.copy(project = false)
      val string = displayMasked(key, mask)

      ("Key: " + displayFull(key)) |:
        ("Mask: " + mask) |:
        ("Current: " + structure.current) |:
        parse(structure, string) {
          case Left(err) => false
          case Right(sk) => sk.scope.project == Select(structure.current)
        }
    }

  property("An unspecified task axis resolves to Global") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = skm.mask.copy(task = false)
      val string = displayMasked(key, mask)

      ("Key: " + displayFull(key)) |:
        ("Mask: " + mask) |:
        parse(structure, string) {
          case Left(err) => false
          case Right(sk) => sk.scope.task == Global
        }
    }

  property("An unspecified configuration axis resolves to the first configuration directly defining the key or else Global") =
    forAllNoShrink(structureDefinedKey) { (skm: StructureKeyMask) =>
      import skm.{ structure, key }
      val mask = ScopeMask(config = false)
      val string = displayMasked(key, mask)
      val resolvedConfig = Resolve.resolveConfig(structure.extra, key.key, mask)(key.scope).config

      ("Key: " + displayFull(key)) |:
        ("Mask: " + mask) |:
        ("Expected configuration: " + resolvedConfig.map(_.name)) |:
        parse(structure, string) {
          case Right(sk) => sk.scope.config == resolvedConfig
          case Left(err) => false
        }
    }

  lazy val structureDefinedKey: Gen[StructureKeyMask] = structureKeyMask { s =>
    for (scope <- TestBuild.scope(s.env); key <- oneOf(s.allAttributeKeys.toSeq)) yield ScopedKey(scope, key)
  }
  def structureKeyMask(genKey: Structure => Gen[ScopedKey[_]])(implicit maskGen: Gen[ScopeMask], structureGen: Gen[Structure]): Gen[StructureKeyMask] =
    for (mask <- maskGen; structure <- structureGen; key <- genKey(structure)) yield new StructureKeyMask(structure, key, mask)
  final class StructureKeyMask(val structure: Structure, val key: ScopedKey[_], val mask: ScopeMask)

  def resolve(structure: Structure, key: ScopedKey[_], mask: ScopeMask): ScopedKey[_] =
    ScopedKey(Resolve(structure.extra, Select(structure.current), key.key, mask)(key.scope), key.key)

  def parseExpected(structure: Structure, s: String, expected: ScopedKey[_], mask: ScopeMask): Prop =
    ("Expected: " + displayFull(expected)) |:
      ("Mask: " + mask) |:
      parse(structure, s) {
        case Left(err) => false
        case Right(sk) => Project.equal(sk, expected, mask)
      }

  def parse(structure: Structure, s: String)(f: Either[String, ScopedKey[_]] => Prop): Prop =
    {
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
  def structureGenF(f: (Seq[Scope], Env, ProjectRef) => Structure)(implicit mkEnv: Gen[Env]): Gen[Structure] =
    structureGen((s, e, p) => Gen.const(f(s, e, p)))
  // Here we're shadowing the in-scope implicit called `mkEnv` for this method
  // so that it will use the passed-in `Gen` rather than the one imported
  // from TestBuild.
  def structureGen(f: (Seq[Scope], Env, ProjectRef) => Gen[Structure])(implicit mkEnv: Gen[Env]): Gen[Structure] =
    for {
      env <- mkEnv
      loadFactor <- choose(0.0, 1.0)
      scopes <- pickN(loadFactor, env.allFullScopes)
      current <- oneOf(env.allProjects.unzip._1)
      structure <- f(scopes, env, current)
    } yield structure

  def pickN[T](load: Double, from: Seq[T]): Gen[Seq[T]] =
    pick((load * from.size).toInt, from)
}
