package sbt.librarymanagement

import hedgehog.*
import hedgehog.runner.*
import CrossVersion.*
import _root_.sbt.librarymanagement.syntax.*
import scala.annotation.nowarn

object CrossVersionTest extends Properties:
  override def tests: List[Test] =
    table("sbtApiVersion", sbtApiVersion)(
      "xyz" -> None,
      "0.12" -> None,
      "0.12.0-SNAPSHOT" -> None,
      "0.12.0-RC1" -> Some((0, 12)),
      "0.12.0" -> Some((0, 12)),
      "0.12.1-SNAPSHOT" -> Some((0, 12)),
      "0.12.1-RC1" -> Some((0, 12)),
      "0.12.1" -> Some((0, 12)),
      "1.0.0-M6" -> None,
      "1.0.0-RC1" -> Some((1, 0)),
      "1.0.0" -> Some((1, 0)),
      "1.0.2-M1" -> Some((1, 0)),
      "1.0.2-RC1" -> Some((1, 0)),
      "1.0.2" -> Some((1, 0)),
      "1.3.0" -> Some((1, 0)),
      "1.10.0" -> Some((1, 0)),
      "2.0.0" -> Some((2, 0)),
    ) ++
      table("partialVersion", partialVersion)(
        "xyz" -> None,
        "2" -> Some((2, 0)),
      ) ++
      table("isSbtApiCompatible", isSbtApiCompatible)(
        "0.12.0-M1" -> false,
        "0.12.0-RC1" -> true,
        "0.12.1-RC1" -> true,
        "1.0.0-M6" -> false,
        "1.0.0-RC1" -> true,
        "1.0.0" -> true,
        "1.0.2-M1" -> true,
        "1.0.2-RC1" -> true,
        "1.0.2" -> true,
        "1.3.0" -> true,
        "1.10.0" -> true,
        "2.0.0" -> true,
      ) ++
      table("binarySbtVersion", binarySbtVersion)(
        "0.11.3" -> "0.11.3",
        "2.0.0" -> "2",
        "2.0.0-M1" -> "2.0.0-M1",
        "2.0.0-RC1" -> "2",
        "2.0.0-RC9-bin-SNAPSHOT" -> "2",
        "2" -> "2",
        "2.1.0-M1" -> "2",
        "2.1.0" -> "2",
        "0.13.1" -> "0.13",
        "1.0.0-M6" -> "1.0.0-M6",
        "1.0.0-RC1" -> "1.0",
        "1.0.0" -> "1.0",
        "1.0.2-M1" -> "1.0",
        "1.0.2-RC1" -> "1.0",
        "1.0.2" -> "1.0",
        "1.3.0" -> "1.0",
        "1.3.0-SNAPSHOT" -> "1.0",
        "1.3.0-A1-B1.1" -> "1.0",
        "1.10.0" -> "1.0",
      ) ++
      table("scalaApiVersion", scalaApiVersion)(
        "xyz" -> None,
        "2.10" -> None,
        "2.10.0-SNAPSHOT" -> None,
        "2.10.0-RC1" -> None,
        "2.10.0" -> Some((2, 10)),
        "2.10.0-1" -> Some((2, 10)),
        "2.10.1-SNAPSHOT" -> Some((2, 10)),
        "2.10.1-RC1" -> Some((2, 10)),
        "2.10.1" -> Some((2, 10)),
      ) ++
      table("isScalaApiCompatible", isScalaApiCompatible)(
        "2.10.0-M1" -> false,
        "2.10.0-RC1" -> false,
        "2.10.1-RC1" -> true,
      ) ++
      table("binaryScalaVersion", binaryScalaVersion)(
        "2.9.2" -> "2.9.2",
        "2.10.0-M1" -> "2.10.0-M1",
        "2.10.0-RC1" -> "2.10.0-RC1",
        "2.10.0" -> "2.10",
        "2.10.1-M1" -> "2.10",
        "2.10.1-RC1" -> "2.10",
        "2.10.1" -> "2.10",
        "2.20170314093845.0-87654321" -> "2.20170314093845",
        "3.0.0-M2" -> "3.0.0-M2",
        "3.0.0-M3-bin-SNAPSHOT" -> "3.0.0-M3",
        "3.0.0-M3-bin-20201215-cbe50b3-NIGHTLY" -> "3.0.0-M3",
        "3.0.0-M3.5-bin-20201215-cbe50b3-NIGHTLY" -> "3.0.0-M3.5",
        "3.0.0-RC1" -> "3.0.0-RC1",
        "3.0.0" -> "3",
        "3.1.0-M1" -> "3",
        "3.1.0-RC1-bin-SNAPSHOT" -> "3",
        "3.1.0-RC1" -> "3",
        "3.1.0" -> "3",
        "3.0.1-RC1" -> "3",
        "3.0.1-M1" -> "3",
        "3.0.1-RC1-bin-SNAPSHOT" -> "3",
        "3.0.1-bin-SNAPSHOT" -> "3",
        "3.0.1-SNAPSHOT" -> "3",
        "4.0.0-M2" -> "4.0.0-M2",
        "3-latest.candidate" -> "3",
        "4-latest.candidate" -> "4",
      ) ++
      table("earlyScalaVersion", earlyScalaVersion)(
        "2.9.2" -> "2.9",
        "2.13.0-M1" -> "2.13",
        "3.0.0-M1" -> "3",
        "4.0.0-M1" -> "4",
        "3-latest.candidate" -> "3",
        "4-latest.candidate" -> "4",
      ) ++
      table("CrossVersion.patch", patchVersion)(
        "2.11.8" -> Some("artefact_2.11.8"),
        "2.11.8-M1" -> Some("artefact_2.11.8-M1"),
        "2.11.8-RC1" -> Some("artefact_2.11.8-RC1"),
        "2.11.8-bin-extra" -> Some("artefact_2.11.8"),
        "2.11.8-M1-bin-extra" -> Some("artefact_2.11.8-M1"),
        "2.11.8-RC1-bin-extra" -> Some("artefact_2.11.8-RC1"),
        "2.11.8-X1.5-bin-extra" -> Some("artefact_2.11.8-X1.5"),
      ) ++
      table("isScalaBinaryCompatibleWith", isScalaBinaryCompatibleWith.tupled)(
        ("2.10.4", "2.10.5") -> true,
        ("2.10.6", "2.10.5") -> true,
        ("2.11.0", "2.10.5") -> false,
        ("3.0.0", "2.10.5") -> false,
        ("3.0.0", "3.1.0") -> false,
        ("3.1.0", "3.0.0") -> true,
        ("3.1.0", "3.1.1") -> true,
        ("3.1.1", "3.1.0") -> true,
        ("2.10.0-M1", "2.10.5") -> false,
        ("2.10.5", "2.10.0-M1") -> false,
        ("2.10.0-M1", "2.10.0-M2") -> false,
        ("2.10.0-M1", "2.11.0-M1") -> false,
        ("3.1.0-M1", "3.0.0") -> true,
        ("3.1.0-M1", "3.1.0") -> false,
        ("3.1.0-M1", "3.1.0-M2") -> false,
        ("3.1.0-M2", "3.1.0-M1") -> false,
      ) ++
      List(
        example(
          "CrossVersion.constant should add a constant to the version",
          constantVersion("duck") ==== Some("artefact_duck")
        ),
        example("Disabled should have structural equality", testDisabledEquality),
        example(
          "CrossVersion.full should have structural equality",
          CrossVersion.full ==== CrossVersion.full
        ),
        example(
          "CrossVersion.binary should have structural equality",
          CrossVersion.binary ==== CrossVersion.binary
        ),
        example(
          "CrossVersion.constant should have structural equality",
          CrossVersion.constant("duck") ==== CrossVersion.constant("duck")
        ),
        example(
          "CrossVersion.for3Use2_13 should have structural equality",
          Result.all(
            List(
              CrossVersion.for3Use2_13 ==== CrossVersion.for3Use2_13,
              CrossVersion.for3Use2_13With("_sjs1", "") ==== CrossVersion
                .for3Use2_13With("_sjs1", ""),
            )
          )
        ),
        example(
          "CrossVersion.for3Use2_13 should use the cross version 2.13 instead of 3",
          CrossVersion(CrossVersion.for3Use2_13, "3.0.0", "3").map(_("artefact")) ====
            Some("artefact_2.13")
        ),
        example(
          "CrossVersion.for3Use2_13 should use the cross version 2.13 instead of 3.0.0-M3",
          CrossVersion(CrossVersion.for3Use2_13, "3.0.0-M3", "3.0.0-M3").map(_("artefact")) ====
            Some("artefact_2.13")
        ),
        example(
          "CrossVersion.for2_13Use3 should have structural equality",
          Result.all(
            List(
              CrossVersion.for2_13Use3 ==== CrossVersion.for2_13Use3,
              CrossVersion.for2_13Use3With("_sjs1", "") ==== CrossVersion
                .for2_13Use3With("_sjs1", ""),
            )
          )
        ),
        example(
          "CrossVersion.for2_13Use3 should use the cross version 3 instead of 2.13",
          CrossVersion(CrossVersion.for2_13Use3, "2.13.4", "2.13").map(_("artefact")) ====
            Some("artefact_3")
        ),
        example(
          "CrossVersion(module, scalaModuleInfo) should name a JVM artifact with the cross suffix",
          named("com.example" %% "foo" % "1.0", Some("jvm")) ==== Some("foo_2.13")
        ),
        example(
          "CrossVersion(module, scalaModuleInfo) should put the platform suffix before the cross suffix",
          named("com.example" %% "foo" % "1.0", Some("sjs1")) ==== Some("foo_sjs1_2.13")
        ),
        example(
          "CrossVersion(module, scalaModuleInfo) should take the platform from the module over the project",
          named(("com.example" %% "foo" % "1.0").platform("native0.5"), Some("sjs1")) ====
            Some("foo_native0.5_2.13")
        ),
        example(
          "CrossVersion(module, scalaModuleInfo) should add no platform suffix when there is no platform",
          named("com.example" %% "foo" % "1.0", None) ==== Some("foo_2.13")
        ),
        example(
          "CrossVersion(module, scalaModuleInfo) should name nothing when the module is not cross-versioned",
          named(
            ("com.example" % "foo" % "1.0").withCrossVersion(CrossVersion.disabled),
            Some("sjs1")
          ) ==== None
        ),
      )

  private def table[A, B](name: String, f: A => B)(cases: (A, B)*): List[Test] =
    cases.toList.map: (input, expected) =>
      example(s"$name for $input should return $expected", f(input) ==== expected)

  @nowarn
  private def testDisabledEquality: Result = Disabled() ==== Disabled()

  private def patchVersion(fullVersion: String): Option[String] =
    CrossVersion(CrossVersion.patch, fullVersion, "dummy").map(fn => fn("artefact"))

  private def constantVersion(value: String): Option[String] =
    CrossVersion(CrossVersion.constant(value), "dummy1", "dummy2").map(fn => fn("artefact"))

  private def scalaInfo(platform: Option[String]) = ScalaModuleInfo(
    "2.13.15",
    "2.13",
    Vector.empty,
    true,
    false,
    true,
    "org.scala-lang",
    Vector.empty,
    platform,
  )

  private def named(module: ModuleID, platform: Option[String]): Option[String] =
    CrossVersion(module, scalaInfo(platform)).map(_(module.name))
end CrossVersionTest
