// import Project.Initialize
import Util._
import Dependencies._
import Scripted._
// import StringUtilities.normalize

def baseVersion = "0.1.0-M2"
def internalPath   = file("internal")

lazy val scalaVersions = Seq(scala210, scala211)

def commonSettings: Seq[Setting[_]] = Seq(
  scalaVersion := "2.11.7",
  // publishArtifact in packageDoc := false,
  resolvers += Resolver.typesafeIvyRepo("releases"),
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.bintrayRepo("sbt", "maven-releases"),
  resolvers += Resolver.url("bintray-sbt-ivy-snapshots", new URL("https://dl.bintray.com/sbt/ivy-snapshots/"))(Resolver.ivyStylePatterns),
  // concurrentRestrictions in Global += Util.testExclusiveRestriction,
  testOptions += Tests.Argument(TestFrameworks.ScalaCheck, "-w", "1"),
  javacOptions in compile ++= Seq("-target", "6", "-source", "6", "-Xlint", "-Xlint:-serial"),
  incOptions := incOptions.value.withNameHashing(true),
  crossScalaVersions := scalaVersions,
  scalacOptions ++= Seq(
    "-encoding", "utf8",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xlint",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-Xfuture",
    "-Yinline-warnings",
    "-Xfatal-warnings",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"),
  commands += publishBridgesAndTest
)

def minimalSettings: Seq[Setting[_]] = commonSettings

// def minimalSettings: Seq[Setting[_]] =
//   commonSettings ++ customCommands ++
//   publishPomSettings ++ Release.javaVersionCheckSettings

def baseSettings: Seq[Setting[_]] =
  minimalSettings
//   minimalSettings ++ baseScalacOptions ++ Licensed.settings ++ Formatting.settings

def testedBaseSettings: Seq[Setting[_]] =
  baseSettings ++ testDependencies

lazy val incrementalcompilerRoot: Project = (project in file(".")).
  // configs(Sxr.sxrConf).
  aggregate(
    incrementalcompiler,
    incrementalcompilerPersist,
    incrementalcompilerCore,
    incrementalcompilerIvyIntegration,
    incrementalcompilerCompile,
    incrementalcompilerCompileCore,
    compilerInterface,
    compilerBridge,
    incrementalcompilerApiInfo,
    incrementalcompilerClasspath,
    incrementalcompilerClassfile,
    incrementalcompilerScripted).
  settings(
    inThisBuild(Seq(
      git.baseVersion := baseVersion,
      bintrayPackage := "incrementalcompiler",
      scmInfo := Some(ScmInfo(url("https://github.com/sbt/incrementalcompiler"), "git@github.com:sbt/incrementalcompiler.git")),
      description := "Incremental compiler of Scala",
      homepage := Some(url("https://github.com/sbt/incrementalcompiler"))
    )),
    minimalSettings,
    otherRootSettings,
    name := "Incrementalcompiler Root",
    publish := {},
    publishLocal := {},
    publishArtifact in Compile := false,
    publishArtifact in Test := false,
    publishArtifact := false
  )

lazy val incrementalcompiler = (project in file("incrementalcompiler")).
  dependsOn(incrementalcompilerCore, incrementalcompilerPersist, incrementalcompilerCompileCore,
    incrementalcompilerClassfile, incrementalcompilerIvyIntegration % "compile->compile;test->test").
  settings(
    testedBaseSettings,
    name := "incrementalcompiler",
    libraryDependencies ++= Seq(utilLogging % "test" classifier "tests", libraryManagement % "test", libraryManagement % "test" classifier "tests")
  )

lazy val incrementalcompilerCompile = (project in file("incrementalcompiler-compile")).
  dependsOn(incrementalcompilerCompileCore, incrementalcompilerCompileCore % "test->test").
  settings(
    testedBaseSettings,
    name := "Incrementalcompiler Compile",
    libraryDependencies ++= Seq(utilTracking)
  )

// Persists the incremental data structures using SBinary
lazy val incrementalcompilerPersist = (project in internalPath / "incrementalcompiler-persist").
  dependsOn(incrementalcompilerCore, incrementalcompilerCore % "test->test").
  settings(
    testedBaseSettings,
    name := "Incrementalcompiler Persist",
    libraryDependencies += sbinary
  )

// Implements the core functionality of detecting and propagating changes incrementally.
//   Defines the data structures for representing file fingerprints and relationships and the overall source analysis
lazy val incrementalcompilerCore = (project in internalPath / "incrementalcompiler-core").
  dependsOn(incrementalcompilerApiInfo, incrementalcompilerClasspath, compilerBridge % Test).
  settings(
    testedBaseSettings,
    libraryDependencies ++= Seq(sbtIO, utilLogging, utilRelation),
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    name := "Incrementalcompiler Core"
  )

lazy val incrementalcompilerIvyIntegration = (project in internalPath / "incrementalcompiler-ivy-integration").
  dependsOn(incrementalcompilerCompileCore).
  settings(
    baseSettings,
    libraryDependencies ++= Seq(libraryManagement, libraryManagement % Test classifier "tests", utilTesting % Test),
    name := "Incrementalcompiler Ivy Integration"
  )

// sbt-side interface to compiler.  Calls compiler-side interface reflectively
lazy val incrementalcompilerCompileCore = (project in internalPath / "incrementalcompiler-compile-core").
  dependsOn(compilerInterface % "compile;test->test", incrementalcompilerClasspath, incrementalcompilerApiInfo, incrementalcompilerClassfile).
  settings(
    testedBaseSettings,
    name := "Incrementalcompiler Compile Core",
    libraryDependencies ++= Seq(scalaCompiler.value % Test, launcherInterface,
      utilLogging, sbtIO, utilLogging % "test" classifier "tests", utilControl),
    unmanagedJars in Test <<= (packageSrc in compilerBridge in Compile).map(x => Seq(x).classpath)
  )

// defines Java structures used across Scala versions, such as the API structures and relationships extracted by
//   the analysis compiler phases and passed back to sbt.  The API structures are defined in a simple
//   format from which Java sources are generated by the sbt-datatype plugin.
lazy val compilerInterface = (project in internalPath / "compiler-interface").
  settings(
    minimalSettings,
    // javaOnlySettings,
    name := "Compiler Interface",
    crossScalaVersions := Seq(scala211),
    libraryDependencies ++= Seq(utilInterface),
    exportJars := true,
    watchSources <++= apiDefinitions,
    resourceGenerators in Compile <+= (version, resourceManaged, streams, compile in Compile) map generateVersionFile,
    apiDefinitions <<= baseDirectory map { base => (base / "definition") :: (base / "other") :: (base / "type") :: Nil },
    crossPaths := false,
    autoScalaLibrary := false
  )

// Compiler-side interface to compiler that is compiled against the compiler being used either in advance or on the fly.
//   Includes API and Analyzer phases that extract source API and relationships.
lazy val compilerBridge: Project = (project in internalPath / "compiler-bridge").
  dependsOn(compilerInterface % "compile;test->test", /*launchProj % "test->test",*/ incrementalcompilerApiInfo % "test->test").
  settings(
    baseSettings,
    libraryDependencies += scalaCompiler.value % "provided",
    autoScalaLibrary := false,
    // precompiledSettings,
    name := "Compiler Bridge",
    exportJars := true,
    // we need to fork because in unit tests we set usejavacp = true which means
    // we are expecting all of our dependencies to be on classpath so Scala compiler
    // can use them while constructing its own classpath for compilation
    fork in Test := true,
    // needed because we fork tests and tests are ran in parallel so we have multiple Scala
    // compiler instances that are memory hungry
    javaOptions in Test += "-Xmx1G",
    libraryDependencies ++= Seq(sbtIO, utilLogging),
    scalaSource in Compile := {
      scalaVersion.value match {
        case v if v startsWith "2.11" => baseDirectory.value / "src" / "main" / "scala"
        case _                        => baseDirectory.value / "src-2.10" / "main" / "scala"
      }
    },
    scalacOptions := {
      scalaVersion.value match {
        case v if v startsWith "2.11" => scalacOptions.value
        case _                        => scalacOptions.value filterNot (Set("-Xfatal-warnings", "-deprecation") contains _)
      }
    }
  )

// defines operations on the API of a source, including determining whether it has changed and converting it to a string
//   and discovery of Projclasses and annotations
lazy val incrementalcompilerApiInfo = (project in internalPath / "incrementalcompiler-apiinfo").
  dependsOn(compilerInterface, incrementalcompilerClassfile).
  settings(
    testedBaseSettings,
    name := "Incrementalcompiler ApiInfo"
  )

// Utilities related to reflection, managing Scala versions, and custom class loaders
lazy val incrementalcompilerClasspath = (project in internalPath / "incrementalcompiler-classpath").
  dependsOn(compilerInterface).
  settings(
    testedBaseSettings,
    name := "Incrementalcompiler Classpath",
    libraryDependencies ++= Seq(scalaCompiler.value,
      Dependencies.launcherInterface,
      sbtIO)
  )

// class file reader and analyzer
lazy val incrementalcompilerClassfile = (project in internalPath / "incrementalcompiler-classfile").
  dependsOn(compilerInterface).
  settings(
    testedBaseSettings,
    libraryDependencies ++= Seq(sbtIO, utilLogging),
    name := "Incrementalcompiler Classfile"
  )

// re-implementation of scripted engine
lazy val incrementalcompilerScripted = (project in internalPath / "incrementalcompiler-scripted").
  dependsOn(incrementalcompiler, incrementalcompilerIvyIntegration % "test->test").
  settings(
    minimalSettings,
    name := "Incrementalcompiler Scripted",
    publish := (),
    publishLocal := (),
    libraryDependencies += utilScripted % "test"
  )

lazy val publishBridgesAndTest = Command.args("publishBridgesAndTest", "<version>") { (state, args) =>
  val version = args mkString ""
  val compilerInterfaceID = compilerInterface.id
  val compilerBridgeID = compilerBridge.id
  val test = s"$compilerInterfaceID/publishLocal" :: s"plz $version incrementalcompilerRoot/test" :: s"plz $version incrementalcompilerRoot/scripted" :: state
  (scalaVersions map (v => s"plz $v $compilerBridgeID/publishLocal") foldRight test) { _ :: _ }
}

lazy val otherRootSettings = Seq(
  Scripted.scriptedPrescripted := { _ => },
  Scripted.scripted <<= scriptedTask,
  Scripted.scriptedUnpublished <<= scriptedUnpublishedTask,
  Scripted.scriptedSource := (sourceDirectory in incrementalcompiler).value / "sbt-test",
  publishAll := {
    val _ = (publishLocal).all(ScopeFilter(inAnyProject)).value
  }
)

def scriptedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  publishAll.value
  doScripted((fullClasspath in incrementalcompilerScripted in Test).value,
    (scalaInstance in incrementalcompilerScripted).value, scriptedSource.value, result, scriptedPrescripted.value)
}

def scriptedUnpublishedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
  val result = scriptedSource(dir => (s: State) => scriptedParser(dir)).parsed
  doScripted((fullClasspath in incrementalcompilerScripted in Test).value,
    (scalaInstance in incrementalcompilerScripted).value, scriptedSource.value, result, scriptedPrescripted.value)
}

lazy val publishAll = TaskKey[Unit]("publish-all")
lazy val publishLauncher = TaskKey[Unit]("publish-launcher")
