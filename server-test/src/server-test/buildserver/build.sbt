ThisBuild / scalaVersion := "2.13.11"

Global / serverLog / logLevel := Level.Debug
Global / cacheStores := Seq.empty

lazy val runAndTest = project.in(file("run-and-test"))
  .settings(
    libraryDependencies += "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.13.11",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    Compile / javaOptions := Vector("Xmx256M"),
    Compile / envVars := Map("KEY" -> "VALUE"),

    Test / javaOptions := Vector("Xmx512M"),
    Test / envVars := Map("KEY_TEST" -> "VALUE_TEST"),
  )
  .dependsOn(util % Optional)

lazy val reportError = project.in(file("report-error"))

lazy val reportWarning = project.in(file("report-warning"))
  .settings(
    scalacOptions += "-deprecation"
  )

// check that the buildTarget/compile request fails with the custom message defined below
lazy val respondError = project.in(file("respond-error"))
  .settings(
    Compile / compile := Def.uncached {
      val _ = (Compile / compile).value
      throw new MessageOnlyException("custom message")
    }
  )

lazy val util = project.settings(
  Compile / classDirectory := baseDirectory.value / "classes"
)

lazy val diagnostics = project

lazy val javaProj = project
  .in(file("java-proj"))
  .settings(
    javacOptions += "-Xlint:all"
  )

// lazy val twirlProj = project
//   .in(file("twirlProj"))
//   .enablePlugins(SbtTwirl)

def somethingBad = throw new MessageOnlyException("I am a bad build target")
// other build targets should not be affected by this bad build target
lazy val badBuildTarget = project.in(file("bad-build-target"))
  .settings(
    Compile / bspBuildTarget := Def.uncached(somethingBad),
    Compile / bspBuildTargetSourcesItem := Def.uncached(somethingBad),
    Compile / bspBuildTargetResourcesItem := Def.uncached(somethingBad),
    Compile / bspBuildTargetDependencySourcesItem := Def.uncached(somethingBad),
    Compile / bspBuildTargetScalacOptionsItem := Def.uncached(somethingBad),
    Compile / bspBuildTargetCompileItem := Def.uncached(somethingBad),
    Compile / bspBuildTargetOutputPathsItem := Def.uncached(somethingBad),
    Compile / bspScalaMainClasses := Def.uncached(somethingBad),
    Test / bspBuildTarget := Def.uncached(somethingBad),
    Test / bspScalaTestClasses := Def.uncached(somethingBad),
  )
