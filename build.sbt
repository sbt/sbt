
import Settings._

def dataclassScalafixV = "0.1.0"

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
  ),
  semanticdbEnabled := true,
  semanticdbVersion := "4.6.0",
  scalafixDependencies += "net.hamnaberg" %% "dataclass-scalafix" % dataclassScalafixV,
  libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always"
))

Global / excludeLintKeys += scriptedBufferLog
Global / excludeLintKeys += scriptedLaunchOpts

val coursierVersion0 = "2.1.0-RC2"

def dataclassGen(data: Reference) = Def.taskDyn {
  val root = (ThisBuild / baseDirectory).value.toURI.toString
  val from = (data / Compile / sourceDirectory).value
  val to = (Compile / sourceManaged).value
  val outFrom = from.toURI.toString.stripSuffix("/").stripPrefix(root)
  val outTo = to.toURI.toString.stripSuffix("/").stripPrefix(root)
  (data / Compile / compile).value
  Def.task {
    (data / Compile / scalafix)
      .toTask(s" --rules GenerateDataClass --out-from=$outFrom --out-to=$outTo")
      .value
    (to ** "*.scala").get
  }
}

def lmIvy = Def.setting {
  "org.scala-sbt" %% "librarymanagement-ivy" % {
    scalaBinaryVersion.value match {
      case "2.12" => "1.3.4"
      case "2.13" => "1.7.0"
      case _      => "2.0.0-alpha2"
    }
  }
}

lazy val preTest = taskKey[Unit]("prep steps before tests")

lazy val definitions = project
  .in(file("modules/definitions"))
  .disablePlugins(MimaPlugin)
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213),
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,
      lmIvy.value,
    ),
    dontPublish,
  )

// FIXME Ideally, we should depend on the same version of io.get-coursier.jniutils:windows-jni-utils that
// io.get-coursier::coursier depends on.
val jniUtilsVersion = "0.3.3"

lazy val `lm-coursier` = project
  .in(file("modules/lm-coursier"))
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213),
    Mima.settings,
    Mima.lmCoursierFilters,
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      "io.get-coursier.jniutils" % "windows-jni-utils-lmcoursier" % jniUtilsVersion,
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,
      // We depend on librarymanagement-ivy rather than just
      // librarymanagement-core to handle the ModuleDescriptor passed
      // to DependencyResolutionInterface.update, which is an
      // IvySbt#Module (seems DependencyResolutionInterface.moduleDescriptor
      // is ignored).
      lmIvy.value,
      "org.scalatest" %% "scalatest" % "3.2.14" % Test
    ),
    Test / exportedProducts := {
      (Test / preTest).value
      (Test / exportedProducts).value
    },
    Test / preTest := {
      (customProtocolForTest212 / publishLocal).value
      (customProtocolForTest213 / publishLocal).value
      (customProtocolJavaForTest / publishLocal).value
    },
    Compile / sourceGenerators += dataclassGen(definitions).taskValue,
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
    Compile / sources := (`lm-coursier` / Compile / sources).value,
    shadedModules ++= Set(
      "io.get-coursier" %% "coursier",
      "io.get-coursier.jniutils" % "windows-jni-utils-lmcoursier"
    ),
    validNamespaces += "lmcoursier",
    validEntries ++= Set(
      // FIXME Ideally, we should just strip those from the resulting JAR…
      "README", // from google-collections via plexus-archiver (see below)
      // from plexus-util via plexus-archiver (see below)
      "licenses/extreme.indiana.edu.license.TXT",
      "licenses/javolution.license.TXT",
      "licenses/thoughtworks.TXT",
      "licenses/",
      // zstd files, pulled via plexus-archiver
      "darwin/",
      "darwin/aarch64/",
      "darwin/aarch64/libzstd-jni-1.5.2-5.dylib",
      "darwin/x86_64/",
      "darwin/x86_64/libzstd-jni-1.5.2-5.dylib",
      "freebsd/",
      "freebsd/amd64/",
      "freebsd/amd64/libzstd-jni-1.5.2-5.so",
      "freebsd/i386/",
      "freebsd/i386/libzstd-jni-1.5.2-5.so",
      "linux/",
      "linux/aarch64/",
      "linux/aarch64/libzstd-jni-1.5.2-5.so",
      "linux/amd64/",
      "linux/amd64/libzstd-jni-1.4.9-3.so",
      "linux/amd64/libzstd-jni-1.4.9-4.so",
      "linux/amd64/libzstd-jni-1.5.2-5.so",
      "linux/arm/",
      "linux/arm/libzstd-jni-1.5.2-5.so",
      "linux/i386/",
      "linux/i386/libzstd-jni-1.5.2-5.so",
      "linux/loongarch64/",
      "linux/loongarch64/libzstd-jni-1.5.2-5.so",
      "linux/mips64/",
      "linux/mips64/libzstd-jni-1.5.2-5.so",
      "linux/ppc64/",
      "linux/ppc64/libzstd-jni-1.5.2-5.so",
      "linux/ppc64le/",
      "linux/ppc64le/libzstd-jni-1.5.2-5.so",
      "linux/s390x/",
      "linux/s390x/libzstd-jni-1.5.2-5.so",
      "win/",
      "win/amd64/",
      "win/amd64/libzstd-jni-1.5.2-5.dll",
      "win/x86/",
      "win/x86/libzstd-jni-1.5.2-5.dll",
    ),
    shadingRules ++= {
      val toShade = Seq(
        "coursier",
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
        "org.tukaani",
        "com.github.plokhotnyuk.jsoniter_scala",
        "scala.cli",
        "com.github.luben.zstd",
        "javax.inject" // hope shading this is fine… It's probably pulled via plexus-archiver, that sbt shouldn't use anyway…
      )
      for (ns <- toShade)
        yield ShadingRule.moveUnder(ns, "lmcoursier.internal.shaded")
    },
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % coursierVersion0,
      "io.get-coursier.jniutils" % "windows-jni-utils-lmcoursier" % jniUtilsVersion,
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.9.0",
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0", // depending on that one so that it doesn't get shaded
      "org.slf4j" % "slf4j-api" % "2.0.6", // depending on that one so that it doesn't get shaded either
      lmIvy.value,
      "org.scalatest" %% "scalatest" % "3.2.14" % Test
    )
  )

lazy val `sbt-coursier-shared` = project
  .in(file("modules/sbt-coursier-shared"))
  .disablePlugins(MimaPlugin)
  .dependsOn(`lm-coursier`)
  .settings(
    plugin,
    generatePropertyFile,
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.8.1" % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

lazy val `sbt-coursier-shared-shaded` = project
  .in(file("modules/sbt-coursier-shared/target/shaded-module"))
  .disablePlugins(MimaPlugin)
  .dependsOn(`lm-coursier-shaded`)
  .settings(
    plugin,
    generatePropertyFile,
    Compile / unmanagedSourceDirectories := (`sbt-coursier-shared` / Compile / unmanagedSourceDirectories).value
  )

lazy val `sbt-lm-coursier` = project
  .in(file("modules/sbt-lm-coursier"))
  .enablePlugins(ScriptedPlugin)
  .disablePlugins(MimaPlugin)
  .dependsOn(`sbt-coursier-shared-shaded`)
  .settings(
    plugin,
    sbtTestDirectory := (`sbt-coursier` / sbtTestDirectory).value,
    scriptedDependencies := {
      scriptedDependencies.value

      // TODO Get those automatically
      // (but shouldn't scripted itself handle that…?)
       (`lm-coursier-shaded` / publishLocal).value
       (`sbt-coursier-shared-shaded` / publishLocal).value
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
      (`lm-coursier` / publishLocal).value
      (`sbt-coursier-shared` / publishLocal).value
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
    definitions,
    `lm-coursier`,
    `lm-coursier-shaded`,
    `sbt-coursier`,
    `sbt-coursier-shared`,
    `sbt-coursier-shared-shaded`,
    `sbt-lm-coursier`
  )
  .settings(
    shared,
    (publish / skip) := true
  )

Global / onChangedBuildSource := ReloadOnSourceChanges
