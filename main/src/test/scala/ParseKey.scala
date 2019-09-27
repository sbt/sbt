/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.Def.{ ScopedKey, displayFull, displayMasked }
import sbt.internal.TestBuild._
import sbt.internal.util.complete.Parser
import sbt.internal.{ Resolve, TestBuild }
import hedgehog.{ Result => Assert, _ }
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.runner._

/**
 * Tests that the scoped key parser in Act can correctly parse a ScopedKey converted by Def.show*Key.
 * This includes properly resolving omitted components.
 */
object ParseKey extends Properties {
  val exampleCount = 10000

  override def tests: List[Test] = List(
    propertyN(
      "An explicitly specified axis is always parsed to that explicit value",
      arbStructureKeyMask.forAll.map(roundtrip),
      exampleCount
    ),
    propertyN(
      "An unspecified project axis resolves to the current project",
      arbStructureKeyMask.forAll.map(noProject),
      exampleCount
    ),
    propertyN(
      "An unspecified task axis resolves to Zero",
      arbStructureKeyMask.forAll.map(noTask),
      exampleCount
    ),
    propertyN(
      "An unspecified configuration axis resolves to the first configuration directly defining the key or else Zero",
      arbStructureKeyMask.forAll.map(noConfig),
      exampleCount
    )
  )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  def roundtrip(skm: StructureKeyMask) = {
    import skm.{ structure, key }

    // if the configuration axis == Zero
    // then a scoped key like `proj/Zero/Zero/name` could render potentially as `Zero/name`
    // which would be interpreted as `Zero/Zero/Zero/name` (Global/name)
    // so we mitigate this by explicitly displaying the configuration axis set to Zero
    val hasZeroConfig = key.scope.config == Zero

    val showZeroConfig = hasZeroConfig || hasAmbiguousLowercaseAxes(key)
    val mask = if (showZeroConfig) skm.mask.copy(project = true) else skm.mask

    val expected = resolve(structure, key, mask)
    parseCheck(structure, key, mask, showZeroConfig)(
      sk =>
        Assert
          .assert(Project.equal(sk, expected, mask))
          .log(s"$sk.key == $expected.key: ${sk.key == expected.key}")
          .log(s"${sk.scope} == ${expected.scope}: ${Scope.equal(sk.scope, expected.scope, mask)}")
    ).log(s"Expected: ${displayFull(expected)}")
  }

  def noProject(skm: StructureKeyMask) = {
    import skm.{ structure, key }
    val mask = skm.mask.copy(project = false)
    // skip when config axis is set to Zero
    val hasZeroConfig = key.scope.config ==== Zero
    val showZeroConfig = hasAmbiguousLowercaseAxes(key)
    parseCheck(structure, key, mask, showZeroConfig)(
      sk =>
        (hasZeroConfig or sk.scope.project ==== Select(structure.current))
          .log(s"Current: ${structure.current}")
    )
  }

  def noTask(skm: StructureKeyMask) = {
    import skm.{ structure, key }
    val mask = skm.mask.copy(task = false)
    parseCheck(structure, key, mask)(_.scope.task ==== Zero)
  }

  def noConfig(skm: StructureKeyMask) = {
    import skm.{ structure, key }
    val mask = ScopeMask(config = false)
    val resolvedConfig = Resolve.resolveConfig(structure.extra, key.key, mask)(key.scope).config
    val showZeroConfig = hasAmbiguousLowercaseAxes(key)
    parseCheck(structure, key, mask, showZeroConfig)(
      sk => (sk.scope.config ==== resolvedConfig) or (sk.scope ==== Scope.GlobalScope)
    ).log(s"Expected configuration: ${resolvedConfig map (_.name)}")
  }

  val arbStructure: Gen[Structure] =
    for {
      env <- mkEnv
      loadFactor <- Gen.double(Range.linearFrac(0.0, 1.0))
      scopes <- pickN(loadFactor, env.allFullScopes)
      current <- oneOf(env.allProjects.unzip._1)
    } yield {
      val settings = structureSettings(scopes, env)
      TestBuild.structure(env, settings, current)
    }

  def structureSettings(scopes: Seq[Scope], env: Env): Seq[Def.Setting[String]] = {
    for {
      scope <- scopes
      t <- env.tasks
    } yield Def.setting(ScopedKey(scope, t.key), Def.value(""))
  }

  final case class StructureKeyMask(structure: Structure, key: ScopedKey[_], mask: ScopeMask)

  val arbStructureKeyMask: Gen[StructureKeyMask] =
    (for {
      structure <- arbStructure
      // NOTE: Generating this after the structure improves shrinking
      mask <- maskGen
      key <- for {
        scope <- TestBuild.scope(structure.env)
        key <- oneOf(structure.allAttributeKeys.toSeq)
      } yield ScopedKey(scope, key)
      skm = StructureKeyMask(structure, key, mask)
    } yield skm)
      .filter(configExistsInIndex)

  private def configExistsInIndex(skm: StructureKeyMask): Boolean = {
    import skm._
    val resolvedKey = resolve(structure, key, mask)
    val proj = resolvedKey.scope.project.toOption
    val maybeResolvedProj = proj.collect {
      case ref: ResolvedReference => ref
    }
    val checkName = for {
      configKey <- resolvedKey.scope.config.toOption
    } yield {
      val configID = Scope.display(configKey)
      // This only works for known configurations or those that were guessed correctly.
      val name = structure.keyIndex.fromConfigIdent(maybeResolvedProj)(configID)
      name == configKey.name
    }
    checkName.getOrElse(true)
  }

  def resolve(structure: Structure, key: ScopedKey[_], mask: ScopeMask): ScopedKey[_] =
    ScopedKey(
      Resolve(structure.extra, Select(structure.current), key.key, mask)(key.scope),
      key.key
    )

  def parseCheck(
      structure: Structure,
      key: ScopedKey[_],
      mask: ScopeMask,
      showZeroConfig: Boolean = false,
  )(f: ScopedKey[_] => Assert): Assert = {
    val s = displayMasked(key, mask, showZeroConfig)
    val parser = makeParser(structure)
    val parsed = Parser.result(parser, s).left.map(_().toString)
    (
      parsed
        .fold(_ => Assert.failure, f)
        .log(s"Key: ${Scope.displayPedantic(key.scope, key.key.label)}")
        .log(s"Mask: $mask")
        .log(s"Key string: '$s'")
        .log(s"Parsed: ${parsed.right.map(displayFull)}")
        .log(s"Structure: $structure")
      )
  }

  // pickN is a function that randomly picks load % items from the "from" sequence.
  // The rest of the tests expect at least one item, so I changed it to return 1 in case of 0.
  def pickN[T](load: Double, from: Seq[T]): Gen[Seq[T]] =
    pick((load * from.size).toInt max 1, from)

  // if both a project and a key share the same name (e.g. "foo")
  // then a scoped key like `foo/<conf>/foo/name` would render as `foo/name`
  // which would be interpreted as `foo/Zero/Zero/name`
  // so we mitigate this by explicitly displaying the configuration axis set to Zero
  def hasAmbiguousLowercaseAxes(key: ScopedKey[_]) = PartialFunction.cond(key.scope) {
    case Scope(Select(ProjectRef(_, proj)), _, Select(key), _) => proj == key.label
  }
}
