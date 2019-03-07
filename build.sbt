
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

lazy val `lm-coursier` = project
  .in(file("modules/lm-coursier"))
  .enablePlugins(ContrabandPlugin)
  .settings(
    shared,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "1.1.0-M13-1",
      // We depend on librarymanagement-ivy rather than just
      // librarymanagement-core to handle the ModuleDescriptor passed
      // to DependencyResolutionInterface.update, which is an
      // IvySbt#Module (seems DependencyResolutionInterface.moduleDescriptor
      // is ignored).
      "org.scala-sbt" %% "librarymanagement-ivy" % "1.2.4",
      "org.scalatest" %% "scalatest" % "3.0.6" % Test
    ),
    managedSourceDirectories in Compile +=
      baseDirectory.value / "src" / "main" / "contraband-scala",
    sourceManaged in (Compile, generateContrabands) := baseDirectory.value / "src" / "main" / "contraband-scala",
    contrabandFormatsForType in generateContrabands in Compile := DatatypeConfig.getFormats
  )

lazy val `sbt-coursier-shared` = project
  .in(file("modules/sbt-coursier-shared"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(`lm-coursier`)
  .settings(
    plugin,
    generatePropertyFile,
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.6.6" % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

lazy val `sbt-lm-coursier` = project
  .in(file("modules/sbt-lm-coursier"))
  .enablePlugins(ScriptedPlugin)
  .dependsOn(`sbt-coursier-shared`)
  .settings(
    plugin,
    sbtTestDirectory := sbtTestDirectory.in(`sbt-coursier`).value,
    scriptedDependencies := {
      scriptedDependencies.value

      // TODO Get those automatically
      // (but shouldn't scripted itself handle that…?)
       publishLocal.in(`lm-coursier`).value
       publishLocal.in(`sbt-coursier-shared`).value
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
      val ver = "1.1.1"
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
    `sbt-coursier`,
    `sbt-coursier-shared`,
    `sbt-lm-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`
  )
  .settings(
    shared,
    skip.in(publish) := true
  )

