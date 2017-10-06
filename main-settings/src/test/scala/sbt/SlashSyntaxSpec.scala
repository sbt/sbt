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

  def withScope[K <: Scoped.ScopingSetting[K]](keyGen: Gen[K]): Arbitrary[K] =
    Arbitrary(Gen.frequency(
      50 -> keyGen,
      1 -> (for (key <- keyGen; scope <- arbitrary[Scope]) yield key in scope)
    ))

  object WithScope {
    implicit def arbInputKey[A: Manifest]: Arbitrary[InputKey[A]] =
      withScope(Gen.identifier map (InputKey[A](_)))

    implicit def arbSettingKey[A: Manifest]: Arbitrary[SettingKey[A]] =
      withScope(Gen.identifier map (SettingKey[A](_)))

    implicit def arbTaskKey[A: Manifest]: Arbitrary[TaskKey[A]] =
      withScope(Gen.identifier map (TaskKey[A](_)))
  }

  object WithoutScope {
    implicit def arbInputKey[A: Manifest]: Arbitrary[InputKey[A]] =
      Arbitrary(Gen.identifier map (InputKey[A](_)))

    implicit def arbSettingKey[A: Manifest]: Arbitrary[SettingKey[A]] =
      Arbitrary(Gen.identifier map (SettingKey[A](_)))

    implicit def arbTaskKey[A: Manifest]: Arbitrary[TaskKey[A]] =
      Arbitrary(Gen.identifier map (TaskKey[A](_)))
  }

  implicit def arbScopeAxis[A: Arbitrary]: Arbitrary[ScopeAxis[A]] =
    Arbitrary(Gen.oneOf[ScopeAxis[A]](This, Zero, arbitrary[A] map (Select(_))))

  implicit val arbAttributeMap: Arbitrary[AttributeMap] = Arbitrary {
    Gen.frequency(
      20 -> AttributeMap.empty,
      1 -> (for (name <- Gen.identifier; isModule <- arbitrary[Boolean])
        yield AttributeMap.empty
            .put(AttributeKey[String]("name"), name)
            .put(AttributeKey[Boolean]("isModule"), isModule)
      )
    )
  }

  implicit def arbScope: Arbitrary[Scope] = Arbitrary(
    for {
      r <- arbitrary[ScopeAxis[Reference]]
      c <- arbitrary[ScopeAxis[ConfigKey]]
      t <- arbitrary[ScopeAxis[AttributeKey[_]]]
      e <- arbitrary[ScopeAxis[AttributeMap]]
    } yield Scope(r, c, t, e)
  )
}
import BuildDSLInstances._

object CustomEquality {
  trait Eq[A] {
    def equal(x: A, y: A): Boolean
  }

  // Avoid reimplementing equality for other standard classes.
  trait EqualLowPriority {
    implicit def universal[A] = (x: A, y: A) => x == y
  }

  object Eq extends EqualLowPriority {
    def apply[A: Eq]: Eq[A] = implicitly

    implicit def eqScoped[A <: Scoped]: Eq[A] = (x, y) => x.scope == y.scope && x.key == y.key
  }

  implicit class AnyWith_===[A](private val x: A) extends AnyVal {
    def ===(y: A)(implicit z: Eq[A]): Boolean = z.equal(x, y)
    def =?(y: A)(implicit z: Eq[A]): Prop = {
      if (x === y) proved else falsified :| s"Expected $x but got $y"
    }
  }

  def expectValue[A: Eq](expected: A)(x: A) =  x.toString |: (expected =? x)
}
import CustomEquality._

object SlashSyntaxSpec extends Properties("SlashSyntax") with SlashSyntax {
  property("Global / key == key in Global") = {
    import WithScope._
    (forAll { (k: SettingKey[String]) => expectValue(k in Global)(Global / k) }
      && forAll { (k: TaskKey[String]) => expectValue(k in Global)(Global / k) }
      && forAll { (k: InputKey[String]) => expectValue(k in Global)(Global / k) })
  }

  property("Reference / key == key in Reference") = {
    import WithScope._
    (forAll { (r: Reference, k: SettingKey[String]) => expectValue(k in r)(r / k) }
      && forAll { (r: Reference, k: TaskKey[String]) => expectValue(k in r)(r / k) }
      && forAll { (r: Reference, k: InputKey[String]) => expectValue(k in r)(r / k) })
  }

  property("Reference / Config / key == key in Reference in Config") = {
    import WithScope._
    (forAll { (r: Reference, c: ConfigKey, k: SettingKey[String]) => expectValue(k in r in c)(r / c / k) }
      && forAll { (r: Reference, c: ConfigKey, k: TaskKey[String]) => expectValue(k in r in c)(r / c / k) }
      && forAll { (r: Reference, c: ConfigKey, k: InputKey[String]) => expectValue(k in r in c)(r / c / k) })
  }

  property("Reference / task / key == key in Reference in task") = {
    import WithoutScope._
    (forAll { (r: Reference, t: TaskKey[String], k: SettingKey[String]) => expectValue(k in (r, t))(r / t / k) }
      && forAll { (r: Reference, t: TaskKey[String], k: TaskKey[String]) => expectValue(k in (r, t))(r / t / k) }
      && forAll { (r: Reference, t: TaskKey[String], k: InputKey[String]) => expectValue(k in (r, t))(r / t / k) })
  }

  property("Reference / Config / task / key == key in Reference in Config in task") = {
    import WithoutScope._
    (forAll { (r: Reference, c: ConfigKey, t: TaskKey[String], k: SettingKey[String]) => expectValue(k in (r, c, t))(r / c / t / k) }
      && forAll { (r: Reference, c: ConfigKey, t: TaskKey[String], k: TaskKey[String]) => expectValue(k in (r, c, t))(r / c / t / k) }
      && forAll { (r: Reference, c: ConfigKey, t: TaskKey[String], k: InputKey[String]) => expectValue(k in (r, c, t))(r / c / t / k) })
  }

  property("Config / key == key in Config") = {
    import WithScope._
    (forAll { (c: ConfigKey, k: SettingKey[String]) => expectValue(k in c)(c / k) }
      && forAll { (c: ConfigKey, k: TaskKey[String]) => expectValue(k in c)(c / k) }
      && forAll { (c: ConfigKey, k: InputKey[String]) => expectValue(k in c)(c / k) })
  }

  property("Config / task / key == key in Config in task") = {
    import WithoutScope._
    (forAll { (c: ConfigKey, t: TaskKey[String], k: SettingKey[String]) => expectValue(k in c in t)(c / t / k) }
      && forAll { (c: ConfigKey, t: TaskKey[String], k: TaskKey[String]) => expectValue(k in c in t)(c / t / k) }
      && forAll { (c: ConfigKey, t: TaskKey[String], k: InputKey[String]) => expectValue(k in c in t)(c / t / k) })
  }

  property("task / key == key in task") = {
    import WithoutScope._
    (forAll { (t: TaskKey[String], k: SettingKey[String]) => expectValue(k in t)(t / k) }
      && forAll { (t: TaskKey[String], k: TaskKey[String]) => expectValue(k in t)(t / k) }
      && forAll { (t: TaskKey[String], k: InputKey[String]) => expectValue(k in t)(t / k) })
  }

  property("Scope / key == key in Scope") = {
    import WithScope._
    (forAll { (s: Scope, k: SettingKey[String]) => expectValue(k in s)(s / k) }
      && forAll { (s: Scope, k: TaskKey[String]) => expectValue(k in s)(s / k) }
      && forAll { (s: Scope, k: InputKey[String]) => expectValue(k in s)(s / k) })
  }

  property("Reference? / key == key in ThisScope.copy(..)") = {
    import WithScope._
    (forAll { (r: ScopeAxis[Reference], k: SettingKey[String]) =>
      expectValue(k in ThisScope.copy(project = r))(r / k) } &&
      forAll { (r: ScopeAxis[Reference], k: TaskKey[String]) =>
      expectValue(k in ThisScope.copy(project = r))(r / k) } &&
      forAll { (r: ScopeAxis[Reference], k: InputKey[String]) =>
      expectValue(k in ThisScope.copy(project = r))(r / k) })
  }

  property("Reference? / ConfigKey? / key == key in ThisScope.copy(..)") = {
    import WithScope._
    (forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], k: SettingKey[String]) =>
      expectValue(k in ThisScope.copy(project = r, config = c))(r / c / k) } &&
      forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], k: TaskKey[String]) =>
        expectValue(k in ThisScope.copy(project = r, config = c))(r / c / k) } &&
      forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], k: InputKey[String]) =>
        expectValue(k in ThisScope.copy(project = r, config = c))(r / c / k) })
  }

  // property("Reference? / AttributeKey? / key == key in ThisScope.copy(..)") = {
  //   import WithScope._
  //   (forAll { (r: ScopeAxis[Reference], t: ScopeAxis[AttributeKey[_]], k: SettingKey[String]) =>
  //     expectValue(k in ThisScope.copy(project = r, task = t))(r / t / k) } &&
  //     forAll { (r: ScopeAxis[Reference], t: ScopeAxis[AttributeKey[_]], k: TaskKey[String]) =>
  //       expectValue(k in ThisScope.copy(project = r, task = t))(r / t / k) } &&
  //     forAll { (r: ScopeAxis[Reference], t: ScopeAxis[AttributeKey[_]], k: InputKey[String]) =>
  //       expectValue(k in ThisScope.copy(project = r, task = t))(r / t / k) }
  // }

  property("Reference? / ConfigKey? / AttributeKey? / key == key in ThisScope.copy(..)") = {
    import WithScope._
    (forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]], k: SettingKey[String]) =>
      expectValue(k in ThisScope.copy(project = r, config = c, task = t))(r / c / t / k) } &&
      forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]], k: TaskKey[String]) =>
        expectValue(k in ThisScope.copy(project = r, config = c, task = t))(r / c / t / k) } &&
      forAll { (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]], k: InputKey[String]) =>
        expectValue(k in ThisScope.copy(project = r, config = c, task = t))(r / c / t / k) })
  }
}
