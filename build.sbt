
import Aliases._
import Settings._
import Publish._

val coursierVersion = "1.1.0-M7"

lazy val `sbt-shared` = project
  .in(file("modules/sbt-shared"))
  .settings(
    plugin,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion,
      "io.get-coursier" %% "coursier-cache" % coursierVersion
    ),
    // because we don't publish for 2.11 the following declaration
    // is more wordy than usual
    // once support for sbt 0.13 is removed, this dependency can go away
    libs ++= {
      val dependency = "com.dwijnand" % "sbt-compat" % "1.2.6"
      val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
      val scalaV = (scalaBinaryVersion in update).value
      val m = Defaults.sbtPluginExtra(dependency, sbtV, scalaV)
      CrossVersion.partialVersion(scalaVersion.value).collect {
        case (2, 10) => m
        case (2, 12) => m
      }.toList
    }
  )

lazy val `sbt-coursier` = project
  .in(file("modules/sbt-coursier"))
  .dependsOn(`sbt-shared`)
  .settings(
    plugin,
    utest,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion,
      "io.get-coursier" %% "coursier-cache" % coursierVersion,
      "io.get-coursier" %% "coursier-extra" % coursierVersion,
      "io.get-coursier" %% "coursier-scalaz-interop" % coursierVersion
    ),
    scriptedDependencies := {
      scriptedDependencies.value

      // TODO Get dependency projects automatically
      // (but shouldn't scripted itself handle that…?)
      publishLocal.in(`sbt-shared`).value
    }
  )

lazy val `sbt-pgp-coursier` = project
  .in(file("modules/sbt-pgp-coursier"))
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    libs += Deps.sbtPgp.value,
    scriptedDependencies := {
      scriptedDependencies.value
      // TODO Get dependency projects automatically
      scriptedDependencies.in(`sbt-coursier`).value
    }
  )

lazy val `sbt-shading` = project
  .in(file("modules/sbt-shading"))
  .enablePlugins(ShadingPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    shading,
    libs += Deps.jarjar % "shaded",
    // dependencies of jarjar-core - directly depending on these so that they don't get shaded
    libs ++= Deps.jarjarTransitiveDeps,
    scriptedDependencies := {
      scriptedDependencies.value
      // TODO Get dependency projects automatically
      scriptedDependencies.in(`sbt-coursier`).value
    }
  )

lazy val coursier = project
  .in(file("."))
  .aggregate(
    `sbt-shared`,
    `sbt-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "sbt-coursier-root"
  )


