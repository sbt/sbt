/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import java.io.File
import scala.util.{ Random, Try }

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.librarymanagement.mavenint.PomExtraAttributeKeys
import _root_.sbt.librarymanagement.{ Artifact, CrossVersion, ModuleID, ScalaModuleInfo }

object GenericPublisherTest extends Properties:
  private val version = "1.0.0"
  private val ordinaryArtifacts = Vector(
    Artifact("mylib", "jar", "jar") -> new File("main.jar"),
    Artifact("mylib-extra", "jar", "jar") -> new File("extra.jar"),
    Artifact("mylib", "src", "jar", "sources") -> new File("sources.jar"),
    Artifact.pom("mylib") -> new File("mylib.pom"),
  )

  override def tests: List[Test] = List(
    example("derives Maven module artifact IDs without POM artifacts", testModuleArtifactIds),
    example("uses cross-versioned individual artifact names", testOrdinaryArtifactPaths),
    example("keeps both legacy sbt plugin artifact names", testLegacyPluginPaths),
    example("puts the platform suffix before the Scala suffix", testPlatformArtifactPath),
    example("uses the snapshot timestamp in every artifact filename", testSnapshotArtifactPaths),
    example("rejects target path collisions before publishing", testPathCollision),
    example("de-duplicates remote snapshot metadata entries", testSnapshotMetadataArtifacts),
    property("Maven target paths do not depend on artifact order", propOrderIndependent),
  )

  private def testModuleArtifactIds: Result =
    val pluginAttributes = Map(
      PomExtraAttributeKeys.ScalaVersionKey -> "2.12",
      PomExtraAttributeKeys.SbtVersionKey -> "1.0",
    )
    val cases = Vector(
      ("mylib_2.12", Map.empty[String, String], "mylib_2.12"),
      ("sbt-plugin", pluginAttributes, "sbt-plugin_2.12_1.0"),
      ("sbt-plugin_2.12_1.0", pluginAttributes, "sbt-plugin_2.12_1.0"),
      ("plain", Map(PomExtraAttributeKeys.ScalaVersionKey -> "2.12"), "plain"),
    )
    Result.all(
      cases
        .map: (moduleName, attributes, expected) =>
          val obtained = GenericPublisher.mavenModuleArtifactId(moduleName, attributes)
          Result
            .assert(obtained == expected)
            .log(s"$moduleName: expected $expected, obtained $obtained")
        .toList
    )

  private def testOrdinaryArtifactPaths: Result =
    val obtained = ordinaryPaths(ordinaryArtifacts).map(_._3).toSet
    val expected = Set(
      "org/example/mylib_2.12/1.0.0/mylib_2.12-1.0.0.jar",
      "org/example/mylib_2.12/1.0.0/mylib-extra_2.12-1.0.0.jar",
      "org/example/mylib_2.12/1.0.0/mylib_2.12-1.0.0-sources.jar",
      "org/example/mylib_2.12/1.0.0/mylib_2.12-1.0.0.pom",
    )
    Result.assert(obtained == expected).log(s"expected: $expected\nobtained: $obtained")

  private def testLegacyPluginPaths: Result =
    val attributes = Map(
      PomExtraAttributeKeys.ScalaVersionKey -> "2.12",
      PomExtraAttributeKeys.SbtVersionKey -> "1.0",
    )
    val moduleArtifactId = GenericPublisher.mavenModuleArtifactId("sbt-alpha", attributes)
    val artifacts = Vector(
      Artifact("sbt-alpha", "jar", "jar") -> new File("legacy.jar"),
      Artifact("sbt-alpha_2.12_1.0", "jar", "jar") -> new File("crossed.jar"),
      Artifact.pom("sbt-alpha") -> new File("legacy.pom"),
      Artifact.pom("sbt-alpha_2.12_1.0") -> new File("crossed.pom"),
    )
    val obtained = GenericPublisher
      .mavenArtifacts("org.example", moduleArtifactId, version, version, artifacts, None)
      .map(_._3)
      .toSet
    val expected = Set(
      "org/example/sbt-alpha_2.12_1.0/1.0.0/sbt-alpha-1.0.0.jar",
      "org/example/sbt-alpha_2.12_1.0/1.0.0/sbt-alpha_2.12_1.0-1.0.0.jar",
      "org/example/sbt-alpha_2.12_1.0/1.0.0/sbt-alpha-1.0.0.pom",
      "org/example/sbt-alpha_2.12_1.0/1.0.0/sbt-alpha_2.12_1.0-1.0.0.pom",
    )
    Result.assert(obtained == expected).log(s"expected: $expected\nobtained: $obtained")
  end testLegacyPluginPaths

  private def testPlatformArtifactPath: Result =
    val module = ModuleID("org.example", "mylib", version)
      .withCrossVersion(CrossVersion.binary)
    val scalaInfo = ScalaModuleInfo(
      "3.3.8",
      "3",
      Vector.empty,
      true,
      false,
      true,
      "org.scala-lang",
      Vector.empty,
      Some("sjs1"),
    )
    val cross = CrossVersion(module, scalaInfo)
    val artifact = Artifact("mylib-extra", "jar", "jar")
    val obtained = GenericPublisher
      .mavenArtifacts(
        "org.example",
        "mylib_sjs1_3",
        version,
        version,
        Vector(artifact -> new File("extra.jar")),
        cross
      )
      .head
      ._3
    Result.assert(
      obtained == "org/example/mylib_sjs1_3/1.0.0/mylib-extra_sjs1_3-1.0.0.jar"
    )
  end testPlatformArtifactPath

  private def testSnapshotArtifactPaths: Result =
    val snapshotVersion = "1.0.0-SNAPSHOT"
    val fileVersion = "1.0.0-20260922.010203-1"
    val artifacts = Vector(
      Artifact("sbt-alpha", "jar", "jar") -> new File("legacy.jar"),
      Artifact("sbt-alpha_2.12_1.0", "jar", "jar") -> new File("crossed.jar"),
    )
    val obtained = GenericPublisher
      .mavenArtifacts(
        "org.example",
        "sbt-alpha_2.12_1.0",
        snapshotVersion,
        fileVersion,
        artifacts,
        None
      )
      .map(_._3)
      .toSet
    val expected = Set(
      s"org/example/sbt-alpha_2.12_1.0/$snapshotVersion/sbt-alpha-$fileVersion.jar",
      s"org/example/sbt-alpha_2.12_1.0/$snapshotVersion/sbt-alpha_2.12_1.0-$fileVersion.jar",
    )
    Result.assert(obtained == expected).log(s"expected: $expected\nobtained: $obtained")
  end testSnapshotArtifactPaths

  private def testPathCollision: Result =
    val artifacts = Vector(
      Artifact("mylib", "jar", "jar") -> new File("main.jar"),
      Artifact("mylib", "bundle", "jar") -> new File("bundle.jar"),
    )
    val failure = Try(ordinaryPaths(artifacts)).failed.toOption
    Result.all(
      List(
        Result.assert(failure.exists(_.isInstanceOf[IllegalArgumentException])),
        Result.assert(failure.exists(_.getMessage.contains("mylib:jar::jar"))),
        Result.assert(failure.exists(_.getMessage.contains("mylib:bundle::jar"))),
      )
    )

  private def testSnapshotMetadataArtifacts: Result =
    val artifacts = Vector("sbt-alpha", "sbt-alpha_2.12_1.0").flatMap: name =>
      Vector(
        Artifact(name, "jar", "jar"),
        Artifact(name, "src", "jar", "sources"),
        Artifact(name, "doc", "jar", "javadoc"),
        Artifact.pom(name),
      )
    val obtained = GenericPublisher.distinctSnapshotArtifacts(artifacts)
    val expected = Vector(
      None -> "jar",
      Some("sources") -> "jar",
      Some("javadoc") -> "jar",
      None -> "pom",
    )
    Result.assert(obtained == expected).log(s"expected: $expected\nobtained: $obtained")

  private def propOrderIndependent: Property =
    for seed <- Gen.int(Range.linear(-1000000, 1000000)).forAll
    yield
      val shuffled = new Random(seed.toLong).shuffle(ordinaryArtifacts).toVector
      val expected = ordinaryPaths(ordinaryArtifacts).map(_._3).sorted
      val obtained = ordinaryPaths(shuffled).map(_._3).sorted
      Result.assert(obtained == expected)

  private def ordinaryPaths(
      artifacts: Vector[(Artifact, File)]
  ): Vector[(Artifact, File, String)] =
    GenericPublisher.mavenArtifacts(
      "org.example",
      "mylib_2.12",
      version,
      version,
      artifacts,
      Some(name => s"${name}_2.12")
    )
end GenericPublisherTest
