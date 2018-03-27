/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import org.scalacheck.{ Test => _, _ }, Arbitrary.arbitrary, Gen._

import java.io.File
import sbt.io.IO
import sbt.{ Scope, ScopeAxis, Scoped, Select, This, Zero }
import sbt.{
  BuildRef,
  LocalProject,
  LocalRootProject,
  ProjectRef,
  Reference,
  RootProject,
  ThisBuild,
  ThisProject
}
import sbt.ConfigKey
import sbt.librarymanagement.syntax._
import sbt.{ InputKey, SettingKey, TaskKey }
import sbt.internal.util.{ AttributeKey, AttributeMap }

object BuildSettingsInstances {
  val genFile: Gen[File] = Gen.oneOf(new File("."), new File("/tmp")) // for now..

  implicit val arbBuildRef: Arbitrary[BuildRef] = Arbitrary(genFile map (f => BuildRef(IO toURI f)))

  implicit val arbProjectRef: Arbitrary[ProjectRef] =
    Arbitrary(for (f <- genFile; id <- Gen.identifier) yield ProjectRef(f, id))

  implicit val arbLocalProject: Arbitrary[LocalProject] =
    Arbitrary(arbitrary[String] map LocalProject)

  implicit val arbRootProject: Arbitrary[RootProject] = Arbitrary(genFile map (RootProject(_)))

  implicit val arbReference: Arbitrary[Reference] = Arbitrary {
    Gen.frequency(
      96 -> arbitrary[BuildRef],
      10271 -> ThisBuild,
      325 -> LocalRootProject,
      2283 -> arbitrary[ProjectRef],
      299 -> ThisProject,
      436 -> arbitrary[LocalProject],
      1133 -> arbitrary[RootProject],
    )
  }

  implicit def arbConfigKey: Arbitrary[ConfigKey] = Arbitrary {
    Gen.frequency(
      2 -> const[ConfigKey](Compile),
      2 -> const[ConfigKey](Test),
      1 -> const[ConfigKey](Runtime),
      1 -> const[ConfigKey](IntegrationTest),
      1 -> const[ConfigKey](Provided),
    )
  }

  implicit def arbAttrKey[A: Manifest]: Arbitrary[AttributeKey[_]] =
    Arbitrary(Gen.identifier map (AttributeKey[A](_)))

  implicit val arbAttributeMap: Arbitrary[AttributeMap] = Arbitrary {
    Gen.frequency(
      20 -> AttributeMap.empty,
      1 -> {
        for (name <- Gen.identifier; isModule <- arbitrary[Boolean])
          yield
            AttributeMap.empty
              .put(AttributeKey[String]("name"), name)
              .put(AttributeKey[Boolean]("isModule"), isModule)
      }
    )
  }

  implicit def arbScopeAxis[A: Arbitrary]: Arbitrary[ScopeAxis[A]] =
    Arbitrary(Gen.oneOf[ScopeAxis[A]](This, Zero, arbitrary[A] map (Select(_))))

  implicit def arbScope: Arbitrary[Scope] = Arbitrary(
    for {
      r <- arbitrary[ScopeAxis[Reference]]
      c <- arbitrary[ScopeAxis[ConfigKey]]
      t <- arbitrary[ScopeAxis[AttributeKey[_]]]
      e <- arbitrary[ScopeAxis[AttributeMap]]
    } yield Scope(r, c, t, e)
  )

  type Key = K forSome { type K <: Scoped.ScopingSetting[K] with Scoped }

  def genInputKey[A: Manifest]: Gen[InputKey[A]] = Gen.identifier map (InputKey[A](_))
  def genSettingKey[A: Manifest]: Gen[SettingKey[A]] = Gen.identifier map (SettingKey[A](_))
  def genTaskKey[A: Manifest]: Gen[TaskKey[A]] = Gen.identifier map (TaskKey[A](_))

  def withScope[K <: Scoped.ScopingSetting[K]](keyGen: Gen[K]): Arbitrary[K] = Arbitrary {
    Gen.frequency(
      5 -> keyGen,
      1 -> (for (key <- keyGen; scope <- arbitrary[Scope]) yield key in scope)
    )
  }

  implicit def arbInputKey[A: Manifest]: Arbitrary[InputKey[A]] = withScope(genInputKey[A])
  implicit def arbSettingKey[A: Manifest]: Arbitrary[SettingKey[A]] = withScope(genSettingKey[A])
  implicit def arbTaskKey[A: Manifest]: Arbitrary[TaskKey[A]] = withScope(genTaskKey[A])

  implicit def arbKey[A: Manifest](
      implicit
      arbInputKey: Arbitrary[InputKey[A]],
      arbSettingKey: Arbitrary[SettingKey[A]],
      arbTaskKey: Arbitrary[TaskKey[A]],
  ): Arbitrary[Key] = Arbitrary {
    def convert[T](g: Gen[T]) = g.asInstanceOf[Gen[Key]]
    Gen.frequency(
      15431 -> convert(arbitrary[InputKey[A]]),
      19645 -> convert(arbitrary[SettingKey[A]]),
      22867 -> convert(arbitrary[TaskKey[A]]),
    )
  }

  object WithoutScope {
    implicit def arbInputKey[A: Manifest]: Arbitrary[InputKey[A]] = Arbitrary(genInputKey[A])
    implicit def arbSettingKey[A: Manifest]: Arbitrary[SettingKey[A]] = Arbitrary(genSettingKey[A])
    implicit def arbTaskKey[A: Manifest]: Arbitrary[TaskKey[A]] = Arbitrary(genTaskKey[A])
  }

  implicit def arbScoped[A: Manifest]: Arbitrary[Scoped] = Arbitrary(arbitrary[Key])
}
