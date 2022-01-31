
import Settings._

inThisBuild(List(
  organization := "io.get-coursier",
  homepage := Some(url("https://github.com/coursier/sbt-coursier")),
  licenses := Seq("Apache 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  developers := List(
    Developer(
      "alexarchambault",
      "Alexandre Archambault",
      "",
      url("https://github.com/alexarchambault")
    )
  )
))

val coursierVersion0 = "2.1.0-M2-1"
val lmVersion = "1.3.4"
val lm2_13Version = "1.5.0-M3"

lazy val `lm-coursier` = project
  .in(file("modules/lm-coursier"))
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213),
    Mima.settings,
    Mima.lmCoursierFilters,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      "io.github.alexarchambault" %% "data-class" % "0.2.5" % Provided,
      // We depend on librarymanagement-ivy rather than just
      // librarymanagement-core to handle the ModuleDescriptor passed
      // to DependencyResolutionInterface.update, which is an
      // IvySbt#Module (seems DependencyResolutionInterface.moduleDescriptor
      // is ignored).
      "org.scala-sbt" %% "librarymanagement-ivy" % {
        if (scalaBinaryVersion.value == "2.12") lmVersion
        else lm2_13Version
      },
      "org.scalatest" %% "scalatest" % "3.2.10" % Test
    ),
    Test / test := {
      (publishLocal in customProtocolForTest212).value
      (publishLocal in customProtocolForTest213).value
      (publishLocal in customProtocolJavaForTest).value
      (Test / test).value
    },
    Test / testOnly := {
      (publishLocal in customProtocolForTest212).value
      (publishLocal in customProtocolForTest213).value
      (publishLocal in customProtocolJavaForTest).value
      (Test / testOnly).evaluated
    }
  )

lazy val `lm-coursier-shaded` = project
  .in(file("modules/lm-coursier/target/shaded-module"))
  .enablePlugins(ShadingPlugin)
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213),
    Mima.settings,
    Mima.lmCoursierFilters,
    Mima.lmCoursierShadedFilters,
    unmanagedSourceDirectories.in(Compile) := unmanagedSourceDirectories.in(Compile).in(`lm-coursier`).value,
    shadedModules += "io.get-coursier" %% "coursier",
    validNamespaces += "lmcoursier",
    validEntries ++= Set(
      // FIXME Ideally, we should just strip those from the resulting JAR…
      "README", // from google-collections via plexus-archiver (see below)
      // from plexus-util via plexus-archiver (see below)
      "licenses/extreme.indiana.edu.license.TXT",
      "licenses/javolution.license.TXT",
      "licenses/thoughtworks.TXT",
      "licenses/"
    ),
    shadingRules ++= {
      val toShade = Seq(
        "coursier",
        "shapeless",
        "argonaut",
        "org.fusesource",
        "macrocompat",
        "io.github.alexarchambault.windowsansi",
        "concurrentrefhashmap",
        // pulled by the plexus-archiver stuff that coursier-cache
        // depends on for now… can hopefully be removed in the future
        "com.google.common",
        "org.apache.commons",
        "org.apache.xbean",
        "org.codehaus",
        "org.iq80",
        "org.tukaani"
      )
      for (ns <- toShade)
        yield ShadingRule.moveUnder(ns, "lmcoursier.internal.shaded")
    },
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      "io.github.alexarchambault" %% "data-class" % "0.2.5" % Provided,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.6.0",
      "org.scala-lang.modules" %% "scala-xml" % "1.3.0", // depending on that one so that it doesn't get shaded
      "org.scala-sbt" %% "librarymanagement-ivy" % {
        if (scalaBinaryVersion.value == "2.12") lmVersion
        else lm2_13Version
      },
      "org.scalatest" %% "scalatest" % "3.2.10" % Test
    )
  )

lazy val `sbt-coursier-shared` = project
  .in(file("modules/sbt-coursier-shared"))
  .disablePlugins(MimaPlugin)
  .dependsOn(`lm-coursier`)
  .settings(
    plugin,
    generatePropertyFile,
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.7.11" % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

lazy val `sbt-coursier-shared-shaded` = project
  .in(file("modules/sbt-coursier-shared/target/shaded-module"))
  .disablePlugins(MimaPlugin)
  .dependsOn(`lm-coursier-shaded`)
  .settings(
    plugin,
    generatePropertyFile,
    unmanagedSourceDirectories.in(Compile) := unmanagedSourceDirectories.in(Compile).in(`sbt-coursier-shared`).value
  )

lazy val `sbt-lm-coursier` = project
  .in(file("modules/sbt-lm-coursier"))
  .enablePlugins(ScriptedPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(`sbt-coursier-shared-shaded`)
  .settings(
    plugin,
    sbtTestDirectory := sbtTestDirectory.in(`sbt-coursier`).value,
    scriptedDependencies := {
      scriptedDependencies.value

      // TODO Get those automatically
      // (but shouldn't scripted itself handle that…?)
       publishLocal.in(`lm-coursier-shaded`).value
       publishLocal.in(`sbt-coursier-shared-shaded`).value
     }
   )

lazy val `sbt-coursier` = project
  .in(file("modules/sbt-coursier"))
  .enablePlugins(ScriptedPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(`sbt-coursier-shared`)
  .settings(
    plugin,
    scriptedDependencies := {
      scriptedDependencies.value

      // TODO Get dependency projects automatically
      // (but shouldn't scripted itself handle that…?)
      publishLocal.in(`lm-coursier`).value
      publishLocal.in(`sbt-coursier-shared`).value
    }
  )

lazy val customProtocolForTest212 = project
  .in(file("modules/custom-protocol-for-test-2-12"))
  .settings(
    sourceDirectory := file("modules/custom-protocol-for-test/src").toPath.toAbsolutePath.toFile,
    scalaVersion := scala212,
    organization := "org.example",
    moduleName := "customprotocol-handler",
    version := "0.1.0",
    dontPublish
  )

lazy val customProtocolForTest213 = project
  .in(file("modules/custom-protocol-for-test-2-13"))
  .settings(
    sourceDirectory := file("modules/custom-protocol-for-test/src").toPath.toAbsolutePath.toFile,
    scalaVersion := scala213,
    organization := "org.example",
    moduleName := "customprotocol-handler",
    version := "0.1.0",
    dontPublish
  )

lazy val customProtocolJavaForTest = project
  .in(file("modules/custom-protocol-java-for-test"))
  .settings(
    crossPaths := false,
    organization := "org.example",
    moduleName := "customprotocoljava-handler",
    version := "0.1.0",
    dontPublish
  )

lazy val `sbt-coursier-root` = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .aggregate(
    `lm-coursier`,
    `lm-coursier-shaded`,
    `sbt-coursier`,
    `sbt-coursier-shared`,
    `sbt-coursier-shared-shaded`,
    `sbt-lm-coursier`
  )
  .settings(
    shared,
    skip.in(publish) := true
  )

