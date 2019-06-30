
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

val coursierVersion0 = "2.0.0-RC2-5"

lazy val `lm-coursier` = project
  // .enablePlugins(ContrabandPlugin)
  .in(file("modules/lm-coursier"))
  .settings(
    shared,
    Mima.settings,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      // We depend on librarymanagement-ivy rather than just
      // librarymanagement-core to handle the ModuleDescriptor passed
      // to DependencyResolutionInterface.update, which is an
      // IvySbt#Module (seems DependencyResolutionInterface.moduleDescriptor
      // is ignored).
      "org.scala-sbt" %% "librarymanagement-ivy" % "1.2.4",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `lm-coursier-shaded` = project
  .in(file("modules/lm-coursier/target/shaded-module"))
  .enablePlugins(ShadingPlugin)
  .settings(
    shared,
    Mima.settings,
    unmanagedSourceDirectories.in(Compile) := unmanagedSourceDirectories.in(Compile).in(`lm-coursier`).value,
    shading,
    shadingNamespace := "lmcoursier.internal.shaded",
    shadeNamespaces ++= Set(
      "coursier",
      "shapeless",
      "argonaut"
    ),
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0 % "shaded",
      "org.scala-lang.modules" %% "scala-xml" % "1.2.0", // depending on that one so that it doesn't get shaded
      "org.scala-sbt" %% "librarymanagement-ivy" % "1.2.4",
      "org.scalatest" %% "scalatest" % "3.0.8" % Test
    )
  )

lazy val `sbt-coursier-shared` = project
  .in(file("modules/sbt-coursier-shared"))
  .dependsOn(`lm-coursier`)
  .settings(
    plugin,
    generatePropertyFile,
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.7.1" % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

lazy val `sbt-coursier-shared-shaded` = project
  .in(file("modules/sbt-coursier-shared/target/shaded-module"))
  .dependsOn(`lm-coursier-shaded`)
  .settings(
    plugin,
    generatePropertyFile,
    unmanagedSourceDirectories.in(Compile) := unmanagedSourceDirectories.in(Compile).in(`sbt-coursier-shared`).value
  )

lazy val `sbt-lm-coursier` = project
  .in(file("modules/sbt-lm-coursier"))
  .enablePlugins(ScriptedPlugin)
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

lazy val `sbt-pgp-coursier` = project
  .in(file("modules/sbt-pgp-coursier"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    libraryDependencies += {
      val sbtv = CrossVersion.binarySbtVersion(sbtVersion.in(pluginCrossBuild).value)
      val sv = scalaBinaryVersion.value
      val ver = "1.1.2-1"
      Defaults.sbtPluginExtra("com.jsuereth" % "sbt-pgp" % ver, sbtv, sv)
    },
    scriptedDependencies := {
      scriptedDependencies.value
      // TODO Get dependency projects automatically
      scriptedDependencies.in(`sbt-coursier`).value
    }
  )

lazy val `sbt-shading` = project
  .in(file("modules/sbt-shading"))
  .enablePlugins(ScriptedPlugin, ShadingPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    shading,
    libraryDependencies += "io.get-coursier.jarjar" % "jarjar-core" % "1.0.1-coursier-1" % "shaded",
    // dependencies of jarjar-core - directly depending on these so that they don't get shaded
    libraryDependencies ++= Seq(
      "com.google.code.findbugs" % "jsr305" % "3.0.2",
      "org.ow2.asm" % "asm-commons" % "7.0",
      "org.ow2.asm" % "asm-util" % "7.0",
      "org.slf4j" % "slf4j-api" % "1.7.26"
    ),
    scriptedDependencies := {
      scriptedDependencies.value
      // TODO Get dependency projects automatically
      scriptedDependencies.in(`sbt-coursier`).value
    }
  )

lazy val `sbt-coursier-root` = project
  .in(file("."))
  .aggregate(
    `lm-coursier`,
    `lm-coursier-shaded`,
    `sbt-coursier`,
    `sbt-coursier-shared`,
    `sbt-coursier-shared-shaded`,
    `sbt-lm-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`
  )
  .settings(
    shared,
    skip.in(publish) := true
  )

