/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package test

import java.io.File
import hedgehog.*
import scala.annotation.nowarn
import scala.reflect.ClassTag
import _root_.sbt.io.IO
import _root_.sbt.ScopeAxis.{ Select, This, Zero }
import _root_.sbt.Scoped.ScopingSetting
import _root_.sbt.librarymanagement.syntax.*
import _root_.sbt.internal.util.{ AttributeKey, AttributeMap }

object BuildSettingsInstances:
  type Key[A1] = ScopingSetting[?] & Scoped

  given Gen[Reference] =
    val genFile: Gen[File] =
      Gen.choice1(Gen.constant(new File(".")), Gen.constant(new File("/tmp")))
    given genBuildRef: Gen[BuildRef] = genFile.map: f =>
      BuildRef(IO.toURI(f))
    given genProjectRef: Gen[ProjectRef] =
      for
        f <- genFile
        id <- identifier
      yield ProjectRef(f, id)
    given genLocalProject: Gen[LocalProject] =
      identifier.map(LocalProject.apply)
    given genRootProject: Gen[RootProject] =
      genFile.map(RootProject.apply)
    Gen.frequency1(
      96 -> genBuildRef.map(x => x: Reference),
      10271 -> Gen.constant(ThisBuild),
      325 -> Gen.constant(LocalRootProject),
      2283 -> genProjectRef.map(x => x: Reference),
      299 -> Gen.constant(ThisProject),
      436 -> genLocalProject.map(x => x: Reference),
      1133 -> genRootProject.map(x => x: Reference),
    )

  @nowarn
  given Gen[ConfigKey] = Gen.frequency1(
    2 -> Gen.constant[ConfigKey](Compile),
    2 -> Gen.constant[ConfigKey](Test),
    1 -> Gen.constant[ConfigKey](Runtime),
    1 -> Gen.constant[ConfigKey](IntegrationTest),
    1 -> Gen.constant[ConfigKey](Provided),
  )

  given genSettingKey[A1: ClassTag]: Gen[SettingKey[A1]] =
    withScope(WithoutScope.genSettingKey)
  given genTaskKey[A1: ClassTag]: Gen[TaskKey[A1]] =
    withScope(WithoutScope.genTaskKey)
  given genInputKey[A1: ClassTag]: Gen[InputKey[A1]] =
    withScope(WithoutScope.genInputKey)
  given genScopeAxis[A1: Gen]: Gen[ScopeAxis[A1]] =
    Gen.choice1[ScopeAxis[A1]](
      Gen.constant(This),
      Gen.constant(Zero),
      summon[Gen[A1]].map(Select(_))
    )

  given genKey[A1: ClassTag]: Gen[Key[A1]] =
    def convert[A2](g: Gen[A2]) = g.asInstanceOf[Gen[Key[A1]]]
    Gen.frequency1(
      15431 -> convert(genInputKey),
      19645 -> convert(genSettingKey),
      22867 -> convert(genTaskKey),
    )

  given genAttrKey: Gen[AttributeKey[?]] =
    identifier.map(AttributeKey[Unit](_))

  given genAttributeMap: Gen[AttributeMap] = Gen.frequency1(
    20 -> Gen.constant(AttributeMap.empty),
    1 ->
      (for
        name <- identifier
        isModule <- Gen.boolean
      yield AttributeMap.empty
        .put(AttributeKey[String]("name"), name)
        .put(AttributeKey[Boolean]("isModule"), isModule))
  )

  given Gen[Scope] =
    for
      r <- summon[Gen[ScopeAxis[Reference]]]
      c <- summon[Gen[ScopeAxis[ConfigKey]]]
      t <- summon[Gen[ScopeAxis[AttributeKey[?]]]]
      e <- summon[Gen[ScopeAxis[AttributeMap]]]
    yield Scope(r, c, t, e)

  def withScope[K <: Scoped.ScopingSetting[K]](keyGen: Gen[K]): Gen[K] =
    Gen.frequency1(
      5 -> keyGen,
      1 -> (for
        key <- keyGen
        scope <- summon[Gen[Scope]]
      yield key.rescope(scope)),
    )

  case class Label(value: String)
  object Label:
    given genLabel: Gen[Label] = identifier.map(Label.apply)
  end Label

  object WithoutScope:
    def genSettingKey[A1: ClassTag]: Gen[SettingKey[A1]] =
      Label.genLabel.map: label =>
        SettingKey[A1](label.value)
    def genTaskKey[A1: ClassTag]: Gen[TaskKey[A1]] =
      Label.genLabel.map: label =>
        TaskKey[A1](label.value)
    def genInputKey[A1: ClassTag]: Gen[InputKey[A1]] =
      Label.genLabel.map: label =>
        InputKey[A1](label.value)
  end WithoutScope

  def identifier: Gen[String] = for
    first <- Gen.char('a', 'z')
    length <- Gen.int(Range.linear(0, 20))
    rest <- Gen.list(
      Gen.frequency1(
        8 -> Gen.char('a', 'z'),
        8 -> Gen.char('A', 'Z'),
        5 -> Gen.char('0', '9'),
        1 -> Gen.constant('_')
      ),
      Range.singleton(length)
    )
  yield (first :: rest).mkString

end BuildSettingsInstances
