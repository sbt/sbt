/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import org.scalacheck.{ Test => _, _ }, Arbitrary.arbitrary, Gen._, Prop._

import java.io.File
import sbt.io.IO
import sbt.SlashSyntax
import sbt.{ Scope, ScopeAxis, Scoped, Select, This, Zero }, Scope.{ Global, ThisScope }
import sbt.{ BuildRef, LocalProject, LocalRootProject, ProjectRef, Reference, RootProject, ThisBuild, ThisProject }
import sbt.ConfigKey
import sbt.librarymanagement.syntax._
import sbt.{ InputKey, SettingKey, TaskKey }
import sbt.internal.util.{ AttributeKey, AttributeMap }

object BuildDSLInstances {
  val genFile: Gen[File] = Gen.oneOf(new File("."), new File("/tmp")) // for now..

  implicit val arbBuildRef: Arbitrary[BuildRef] = Arbitrary(genFile map (f => BuildRef(IO toURI f)))

  implicit val arbProjectRef: Arbitrary[ProjectRef] =
    Arbitrary(for (f <- genFile; id <- Gen.identifier) yield ProjectRef(f, id))

  implicit val arbLocalProject: Arbitrary[LocalProject] =
    Arbitrary(arbitrary[String] map LocalProject)

  implicit val arbRootProject: Arbitrary[RootProject] = Arbitrary(genFile map (RootProject(_)))

  implicit val arbReference: Arbitrary[Reference] = Arbitrary {
    Gen.frequency(
      1 -> arbitrary[BuildRef],     // 96
      100 -> ThisBuild,             // 10,271
      3 -> LocalRootProject,        // 325
      23 -> arbitrary[ProjectRef],  // 2,283
      3 -> ThisProject,             // 299
      4 -> arbitrary[LocalProject], // 436
      11 -> arbitrary[RootProject], // 1,133
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

  implicit def arbScoped[A: Manifest](implicit
      arbInputKey: Arbitrary[InputKey[A]],
      arbSettingKey: Arbitrary[SettingKey[A]],
      arbTaskKey: Arbitrary[TaskKey[A]],
  ): Arbitrary[Scoped] = {
    Arbitrary(Gen.frequency(
      15 -> arbitrary[InputKey[A]],   // 15,431
      20 -> arbitrary[SettingKey[A]], // 19,645
      23 -> arbitrary[TaskKey[A]],    // 22,867
    ))
  }

  object WithoutScope {
    implicit def arbInputKey[A: Manifest]: Arbitrary[InputKey[A]] = Arbitrary(genInputKey[A])
    implicit def arbSettingKey[A: Manifest]: Arbitrary[SettingKey[A]] = Arbitrary(genSettingKey[A])
    implicit def arbTaskKey[A: Manifest]: Arbitrary[TaskKey[A]] = Arbitrary(genTaskKey[A])
  }
}
import BuildDSLInstances._

object SlashSyntaxSpec extends Properties("SlashSyntax") with SlashSyntax {
  type Key[K] = Scoped.ScopingSetting[K] with Scoped

  property("Global / key == key in Global") = {
    def check[K <: Key[K]: Arbitrary] = forAll((k: K) => expectValue(k in Global)(Global / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference / key == key in Reference") = {
    def check[K <: Key[K]: Arbitrary] = forAll((r: Reference, k: K) => expectValue(k in r)(r / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference / Config / key == key in Reference in Config") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((r: Reference, c: ConfigKey, k: K) => expectValue(k in r in c)(r / c / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference / task.key / key == key in Reference in task") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((r: Reference, t: Scoped, k: K) => expectValue(k in (r, t))(r / t.key / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference / task / key ~= key in Reference in task") = {
    import WithoutScope._
    def check[T <: Key[T]: Arbitrary, K <: Key[K]: Arbitrary] =
      forAll((r: Reference, t: T, k: K) => expectValue(k in (r, t))(r / t / k))
    (true
        && check[InputKey[String], InputKey[String]]
        && check[InputKey[String], SettingKey[String]]
        && check[InputKey[String], TaskKey[String]]
        && check[SettingKey[String], InputKey[String]]
        && check[SettingKey[String], SettingKey[String]]
        && check[SettingKey[String], TaskKey[String]]
        && check[TaskKey[String], InputKey[String]]
        && check[TaskKey[String], SettingKey[String]]
        && check[TaskKey[String], TaskKey[String]]
    )
  }

  property("Reference / Config / task.key / key == key in Reference in Config in task") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((r: Reference, c: ConfigKey, t: Scoped, k: K) =>
        expectValue(k in (r, c, t))(r / c / t.key / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference / Config / task / key ~= key in Reference in Config in task") = {
    import WithoutScope._
    def check[T <: Key[T]: Arbitrary, K <: Key[K]: Arbitrary] =
      forAll((r: Reference, c: ConfigKey, t: T, k: K) => expectValue(k in (r, c, t))(r / c / t / k))
    (true
        && check[InputKey[String], InputKey[String]]
        && check[InputKey[String], SettingKey[String]]
        && check[InputKey[String], TaskKey[String]]
        && check[SettingKey[String], InputKey[String]]
        && check[SettingKey[String], SettingKey[String]]
        && check[SettingKey[String], TaskKey[String]]
        && check[TaskKey[String], InputKey[String]]
        && check[TaskKey[String], SettingKey[String]]
        && check[TaskKey[String], TaskKey[String]]
    )
  }

  property("Config / key == key in Config") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((c: ConfigKey, k: K) => expectValue(k in c)(c / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Config / task.key / key == key in Config in task") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((c: ConfigKey, t: Scoped, k: K) => expectValue(k in c in t)(c / t.key / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Config / task / key ~= key in Config in task") = {
    import WithoutScope._
    def check[T <: Key[T]: Arbitrary, K <: Key[K]: Arbitrary] =
      forAll((c: ConfigKey, t: T, k: K) => expectValue(k in c in t)(c / t / k))
    (true
        && check[InputKey[String], InputKey[String]]
        && check[InputKey[String], SettingKey[String]]
        && check[InputKey[String], TaskKey[String]]
        && check[SettingKey[String], InputKey[String]]
        && check[SettingKey[String], SettingKey[String]]
        && check[SettingKey[String], TaskKey[String]]
        && check[TaskKey[String], InputKey[String]]
        && check[TaskKey[String], SettingKey[String]]
        && check[TaskKey[String], TaskKey[String]]
    )
  }

  property("task.key / key == key in task") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((t: Scoped, k: K) => expectValue(k in t)(t.key / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("task / key ~= key in task") = {
    import WithoutScope._
    def check[T <: Key[T]: Arbitrary, K <: Key[K]: Arbitrary] =
      forAll((t: T, k: K) => expectValue(k in t)(t / k))
    (true
        && check[InputKey[String], InputKey[String]]
        && check[InputKey[String], SettingKey[String]]
        && check[InputKey[String], TaskKey[String]]
        && check[SettingKey[String], InputKey[String]]
        && check[SettingKey[String], SettingKey[String]]
        && check[SettingKey[String], TaskKey[String]]
        && check[TaskKey[String], InputKey[String]]
        && check[TaskKey[String], SettingKey[String]]
        && check[TaskKey[String], TaskKey[String]]
    )
  }

  property("Scope / key == key in Scope") = {
    def check[K <: Key[K]: Arbitrary] = forAll((s: Scope, k: K) => expectValue(k in s)(s / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference? / key == key in ThisScope.copy(..)") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((r: ScopeAxis[Reference], k: K) =>
        expectValue(k in ThisScope.copy(project = r))(r / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  property("Reference? / ConfigKey? / key == key in ThisScope.copy(..)") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll((r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], k: K) =>
        expectValue(k in ThisScope.copy(project = r, config = c))(r / c / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

//  property("Reference? / AttributeKey? / key == key in ThisScope.copy(..)") = {
//    def check[K <: Key[K]: Arbitrary] =
//      forAll(
//        (r: ScopeAxis[Reference], t: ScopeAxis[AttributeKey[_]], k: K) =>
//          expectValue(k in ThisScope.copy(project = r, task = t))(r / t / k))
//    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
//  }

  property("Reference? / ConfigKey? / AttributeKey? / key == key in ThisScope.copy(..)") = {
    def check[K <: Key[K]: Arbitrary] =
      forAll(
        (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]], k: K) =>
          expectValue(k in ThisScope.copy(project = r, config = c, task = t))(r / c / t / k))
    check[InputKey[String]] && check[SettingKey[String]] && check[TaskKey[String]]
  }

  def expectValue(expected: Scoped)(x: Scoped) = {
    val equals = x.scope == expected.scope && x.key == expected.key
    if (equals) proved else falsified :| s"Expected $expected but got $x"
  }
}
