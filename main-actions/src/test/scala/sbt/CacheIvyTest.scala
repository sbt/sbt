/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.scalacheck._
import org.scalacheck.Arbitrary._
import Prop._
import sbt.librarymanagement._
import sjsonnew.shaded.scalajson.ast.unsafe.JValue

class CacheIvyTest extends Properties("CacheIvy") {
  import sbt.util.{ CacheStore, SingletonCache }
  import SingletonCache._

  import sjsonnew._
  import sjsonnew.support.scalajson.unsafe.Converter

  private class InMemoryStore(converter: SupportConverter[JValue]) extends CacheStore {
    private var content: JValue = _
    override def delete(): Unit = ()
    override def close(): Unit = ()

    override def read[T: JsonReader](): T =
      try converter.fromJsonUnsafe[T](content)
      catch { case t: Throwable => t.printStackTrace(); throw t }

    override def read[T: JsonReader](default: => T): T =
      try read[T]()
      catch { case _: Throwable => default }

    override def write[T: JsonWriter](value: T): Unit =
      content = converter.toJsonUnsafe(value)
  }

  private def testCache[T: JsonFormat, U](f: (SingletonCache[T], CacheStore) => U)(
      implicit cache: SingletonCache[T]): U = {
    val store = new InMemoryStore(Converter)
    f(cache, store)
  }

  private def cachePreservesEquality[T: JsonFormat](m: T,
                                                    eq: (T, T) => Prop,
                                                    str: T => String): Prop = testCache[T, Prop] {
    (cache, store) =>
      cache.write(store, m)
      val out = cache.read(store)
      eq(out, m) :| s"Expected: ${str(m)}" :| s"Got: ${str(out)}"
  }

  implicit val arbConfigRef: Arbitrary[ConfigRef] = Arbitrary(
    for {
      n <- Gen.alphaStr
    } yield ConfigRef(n)
  )

  implicit val arbExclusionRule: Arbitrary[InclExclRule] = Arbitrary(
    for {
      o <- Gen.alphaStr
      n <- Gen.alphaStr
      a <- Gen.alphaStr
      v <- arbCrossVersion.arbitrary
      cs <- arbitrary[List[ConfigRef]]
    } yield InclExclRule(o, n, a, cs.toVector, v)
  )

  implicit val arbCrossVersion: Arbitrary[CrossVersion] = Arbitrary {
    // Actual functions don't matter, just Disabled vs Binary vs Full
    Gen.oneOf(Disabled(), Binary(), Full())
  }

  implicit val arbArtifact: Arbitrary[Artifact] = Arbitrary {
    for {
      (n, t, e, cls) <- arbitrary[(String, String, String, String)]
    } yield Artifact(n, t, e, cls) // keep it simple
  }

  implicit val arbModuleID: Arbitrary[ModuleID] = Arbitrary {
    for {
      o <- Gen.identifier
      n <- Gen.identifier
      r <- for { n <- Gen.numChar; ns <- Gen.numStr } yield n + ns
      cs <- arbitrary[Option[String]]
      branch <- arbitrary[Option[String]]
      isChanging <- arbitrary[Boolean]
      isTransitive <- arbitrary[Boolean]
      isForce <- arbitrary[Boolean]
      explicitArtifacts <- Gen.listOf(arbitrary[Artifact])
      exclusions <- Gen.listOf(arbitrary[InclExclRule])
      inclusions <- Gen.listOf(arbitrary[InclExclRule])
      extraAttributes <- Gen.mapOf(arbitrary[(String, String)])
      crossVersion <- arbitrary[CrossVersion]
    } yield
      ModuleID(
        organization = o,
        name = n,
        revision = r,
        configurations = cs,
        isChanging = isChanging,
        isTransitive = isTransitive,
        isForce = isForce,
        explicitArtifacts = explicitArtifacts.toVector,
        inclusions = inclusions.toVector,
        exclusions = exclusions.toVector,
        extraAttributes = extraAttributes,
        crossVersion = crossVersion,
        branchName = branch
      )
  }

  property("moduleIDFormat") = forAll { (m: ModuleID) =>
    def str(m: ModuleID) = {
      import m._
      s"ModuleID($organization, ${m.name}, $revision, $configurations, $isChanging, $isTransitive, $isForce, $explicitArtifacts, $exclusions, " +
        s"$inclusions, $extraAttributes, $crossVersion, $branchName)"
    }
    def eq(a: ModuleID, b: ModuleID): Prop = {
      def rest = a.withCrossVersion(b.crossVersion) == b
      (a.crossVersion, b.crossVersion) match {
        case (_: Disabled, _: Disabled) => rest
        case (_: Binary, _: Binary)     => rest
        case (_: Full, _: Full)         => rest
        case (a, b)                     => Prop(false) :| s"CrossVersions don't match: $a vs $b"
      }

    }
    import sbt.librarymanagement.LibraryManagementCodec._
    cachePreservesEquality(m, eq _, str)
  }
}
