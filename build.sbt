
import Settings._

def dataclassScalafixV = "0.1.0"

inThisBuild(List(
  organization := "org.scala-sbt",
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
  semanticdbVersion := "4.5.9",
  scalafixDependencies += "net.hamnaberg" %% "dataclass-scalafix" % dataclassScalafixV,
  scalafixScalaBinaryVersion := {
    (ThisBuild / scalaBinaryVersion).value match {
      case "3" => "2.13"
      case v   => v
    }
  },
  version := "2.0.0-alpha4-SNAPSHOT",
  scalaVersion := scala3,
))

ThisBuild / assemblyMergeStrategy := {
  case PathList("lmcoursier", "internal", "shaded", "org", "fusesource", xs @ _*) => MergeStrategy.first
  // case PathList("lmcoursier", "internal", "shaded", "package.class") => MergeStrategy.first
  // case PathList("lmcoursier", "internal", "shaded", "package$.class") => MergeStrategy.first
  case PathList("com", "github") => MergeStrategy.discard
  case PathList("com", "jcraft") => MergeStrategy.discard
  case PathList("com", "lmax") => MergeStrategy.discard
  case PathList("com", "sun") => MergeStrategy.discard
  case PathList("com", "swoval") => MergeStrategy.discard
  case PathList("com", "typesafe") => MergeStrategy.discard
  case PathList("gigahorse") => MergeStrategy.discard
  case PathList("jline") => MergeStrategy.discard
  case PathList("scala") => MergeStrategy.discard
  case PathList("sjsonnew") => MergeStrategy.discard
  case PathList("xsbti") => MergeStrategy.discard
  case PathList("META-INF", "native", xs @ _*) => MergeStrategy.first
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

val coursierVersion0 = "2.1.0-M5"
val lmVersion = "1.3.4"
val lm2_13Version = "1.5.0-M3"
val lm3Version = "2.0.0-alpha7"

lazy val scalafixGen = Def.taskDyn {
  val root = (ThisBuild / baseDirectory).value.toURI.toString
  val from = (Compile / sourceDirectory).value
  val to = (Compile / sourceManaged).value
  val outFrom = from.toURI.toString.stripSuffix("/").stripPrefix(root)
  val outTo = to.toURI.toString.stripSuffix("/").stripPrefix(root)
  Def.task {
    (Compile / scalafix)
      .toTask(s" https://raw.githubusercontent.com/hamnis/dataclass-scalafix/9d1bc56b0b53c537293f1218d5600a2e987ee82a/rules/src/main/scala/fix/GenerateDataClass.scala --out-from=$outFrom --out-to=$outTo")
      .value
    (to ** "*.scala").get
  }
}

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
      case "2.12" => lmVersion
      case "2.13" => lm2_13Version
      case  _     => lm3Version
    }
  }
}

lazy val definitions = project
  .in(file("modules/definitions"))
  .disablePlugins(MimaPlugin)
  .settings(
    scalaVersion := scala3,
    crossScalaVersions := Seq(scala212, scala213, scala3),
    libraryDependencies ++= Seq(
      ("io.get-coursier" %% "coursier" % coursierVersion0).cross(CrossVersion.for3Use2_13),
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,
      lmIvy.value % Provided,
    ),
    conflictWarning := ConflictWarning.disable,
    dontPublish,
  )

lazy val `lm-coursier` = project
  .in(file("modules/lm-coursier"))
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213, scala3),
    Mima.settings,
    Mima.lmCoursierFilters,
    libraryDependencies ++= Seq(
      ("io.get-coursier" %% "coursier" % coursierVersion0).cross(CrossVersion.for3Use2_13),
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,

      // We depend on librarymanagement-ivy rather than just
      // librarymanagement-core to handle the ModuleDescriptor passed
      // to DependencyResolutionInterface.update, which is an
      // IvySbt#Module (seems DependencyResolutionInterface.moduleDescriptor
      // is ignored).
      lmIvy.value,
      ("org.scalatest" %% "scalatest" % "3.2.13" % Test).cross(CrossVersion.for3Use2_13),
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
    },
    Compile / sourceGenerators += dataclassGen(definitions).taskValue,
  )

lazy val `lm-coursier-shaded-publishing` = project
  .in(file("modules/lm-coursier/target/shaded-publishing-module"))
  .settings(
    name := "librarymanagement-coursier",
    crossScalaVersions := Seq(scala212, scala213, scala3),
    Compile / packageBin := (`lm-coursier-shaded` / assembly).value,
  )

lazy val `lm-coursier-shaded` = project
  .in(file("modules/lm-coursier/target/shaded-module"))
  .settings(
    shared,
    crossScalaVersions := Seq(scala212, scala213, scala3),
    Mima.settings,
    Mima.lmCoursierFilters,
    Mima.lmCoursierShadedFilters,
    Compile / sources := (`lm-coursier` / Compile / sources).value,
    // shadedModules += "io.get-coursier" %% "coursier",
    // validNamespaces += "lmcoursier",
    // validEntries ++= Set(
    //   // FIXME Ideally, we should just strip those from the resulting JAR…
    //   "README", // from google-collections via plexus-archiver (see below)
    //   // from plexus-util via plexus-archiver (see below)
    //   "licenses/extreme.indiana.edu.license.TXT",
    //   "licenses/javolution.license.TXT",
    //   "licenses/thoughtworks.TXT",
    //   "licenses/"
    // ),
    assemblyShadeRules := {
      val toShade = Seq(
        "coursier",
        "shapeless",
        "argonaut",
        "org.fusesource",
        "macrocompat",
        "io.github.alexarchambault.windowsansi",
        "concurrentrefhashmap",
        "com.github.ghik",
        // pulled by the plexus-archiver stuff that coursier-cache
        // depends on for now… can hopefully be removed in the future
        "com.google.common",
        "com.jcraft",
        "com.lmax",
        "org.apache.commons",
        "org.apache.xbean",
        "org.codehaus",
        "org.iq80",
        "org.tukaani",
        "scala.collection.compat",
        "scala.util.control.compat",
        "scala.xml",
      )
      for (ns <- toShade)
        yield ShadeRule.rename(ns + ".**" -> s"lmcoursier.internal.shaded.$ns.@1").inAll
    },
    libraryDependencies ++= Seq(
      ("io.get-coursier" %% "coursier" % coursierVersion0).cross(CrossVersion.for3Use2_13),
      "net.hamnaberg" %% "dataclass-annotation" % dataclassScalafixV % Provided,
      lmIvy.value % Provided,
      "org.scalatest" %% "scalatest" % "3.2.13" % Test,
    ),
    conflictWarning := ConflictWarning.disable,
    dontPublish,
  )

lazy val `sbt-coursier-shared` = project
  .in(file("modules/sbt-coursier-shared"))
  .disablePlugins(MimaPlugin)
  .dependsOn(`lm-coursier`)
  .settings(
    plugin,
    generatePropertyFile,
    libraryDependencies += "com.lihaoyi" %% "utest" % "0.8.0" % Test,
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

