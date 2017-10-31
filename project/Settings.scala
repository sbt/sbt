
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{sbtLauncher, scriptedBufferLog, ScriptedLaunchConf, scriptedLaunchOpts}

import com.lightbend.sbt.SbtProguard.autoImport._
import com.typesafe.sbt.pgp._
import coursier.ShadingPlugin.autoImport._

import Aliases._

object Settings {

  lazy val scalazBintrayRepository = {
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases"
  }

  def sonatypeRepository(name: String) = {
    resolvers += Resolver.sonatypeRepo(name)
  }

  lazy val localM2Repository = {
    resolvers += Resolver.mavenLocal
  }

  lazy val javaScalaPluginShared = Publish.released ++ Seq(
    organization := "io.get-coursier",
    scalazBintrayRepository,
    sonatypeRepository("releases"),
    crossScalaVersions := Seq("2.12.1", "2.11.11", "2.10.6"), // defined for all projects to trump sbt-doge
    scalacOptions ++= {
      val targetJvm = scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Seq("-target:jvm-1.6")
        case _ =>
          Seq()
      }
  
      targetJvm ++ Seq("-feature", "-deprecation")
    },
    javacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Seq(
            "-source", "1.6",
            "-target", "1.6"
          )
        case _ =>
          Seq()
      }
    },
    javacOptions.in(Keys.doc) := Seq()
  )

  lazy val shared = javaScalaPluginShared ++ Seq(
    scalaVersion := "2.12.1",
    libs ++= {
      if (scalaBinaryVersion.value == "2.10")
        Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
      else
        Seq()
    }
  )

  lazy val pureJava = javaScalaPluginShared ++ Seq(
    crossPaths := false,
    autoScalaLibrary := false
  )

  lazy val generatePropertyFile = 
    resourceGenerators.in(Compile) += Def.task {
      import sys.process._

      val dir = classDirectory.in(Compile).value / "coursier"
      val ver = version.value
  
      val f = dir / "coursier.properties"
      dir.mkdirs()

      val p = new java.util.Properties
  
      p.setProperty("version", ver)
      p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)
  
      val w = new java.io.FileOutputStream(f)
      p.store(w, "Coursier properties")
      w.close()
  
      state.value.log.info(s"Wrote $f")
  
      Seq(f)
    }

  lazy val coursierPrefix = {
    name := "coursier-" + name.value
  }

  lazy val scalaXmlIfNecessary = Seq(
    libs ++= {
      if (scalaBinaryVersion.value == "2.10") Seq()
      else Seq(Deps.scalaXml)
    }
  )

  lazy val quasiQuotesIfNecessary = Seq(
    libs ++= {
      if (scalaBinaryVersion.value == "2.10")
        // directly depending on that one so that it doesn't get shaded
        Seq(Deps.quasiQuotes)
      else
        Nil
    }
  )

  lazy val noTests = Seq(
    test.in(Test) := {},
    testOnly.in(Test) := {}
  )

  lazy val utest = Seq(
    libs += CrossDeps.utest.value % "test",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  lazy val webjarBintrayRepository = {
    resolvers += "Webjars Bintray" at "https://dl.bintray.com/webjars/maven/"
  }

  def renameMainJar(name: String) = {
    artifactName := {
      val artifactName0 = artifactName.value
      (sv, m, artifact) =>
        if (artifact.`type` == "jar" && artifact.extension == "jar")
          name
        else
          artifactName0(sv, m, artifact)
    }
  }

  lazy val divertThingsPlugin = {

    val actualSbtBinaryVersion = Def.setting(
      sbtBinaryVersion.in(pluginCrossBuild).value.split('.').take(2).mkString(".")
    )

    val sbtPluginScalaVersions = Map(
      "0.13" -> "2.10",
      "1.0"  -> "2.12"
    )

    val sbtScalaVersionMatch = Def.setting {
      val sbtVer = actualSbtBinaryVersion.value
      val scalaVer = scalaBinaryVersion.value

      sbtPluginScalaVersions.get(sbtVer).toSeq.contains(scalaVer)
    }

    Seq(
      baseDirectory := {
        val baseDir = baseDirectory.value

        if (sbtScalaVersionMatch.value)
          baseDir
        else
          baseDir / "target" / "dummy"
      },
      // Doesn't work, the second publish or publishLocal seem not to reference the previous implementation of the key.
      // This only seems to prevent ivy.xml files to be published locally anyway…
      // See also similar case in Publish.scala.
      // publish := Def.taskDyn {
      //   if (sbtScalaVersionMatch.value)
      //     publish
      //   else
      //     Def.task(())
      // },
      // publishLocal := Def.taskDyn {
      //   if (sbtScalaVersionMatch.value)
      //     publishLocal
      //   else
      //     Def.task(())
      // },
      publishArtifact := {
        sbtScalaVersionMatch.value && publishArtifact.value
      }
    )
  }

  val sbt013Version = "0.13.8"
  val sbt10Version = "1.0.2"

  lazy val plugin =
    javaScalaPluginShared ++
    divertThingsPlugin ++
    withScriptedTests ++
    Seq(
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
      ),
      scriptedBufferLog := false,
      sbtPlugin := {
        scalaBinaryVersion.value match {
          case "2.10" | "2.12" => true
          case _ => false
        }
      },
      sbtVersion.in(pluginCrossBuild) := {
        scalaBinaryVersion.value match {
          case "2.10" => sbt013Version
          case "2.12" => sbt10Version
          case _ => sbtVersion.in(pluginCrossBuild).value
        }
      },
      resolvers ++= Seq(
        // added so that 2.10 artifacts of the other modules can be found by
        // the too-naive-for-now inter-project resolver of the coursier SBT plugin
        Resolver.sonatypeRepo("snapshots"),
        // added for sbt-scripted to be fine even with ++2.11.x
        Resolver.typesafeIvyRepo("releases")
      )
    )

  lazy val shading =
    inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings) ++
       // ytf does this have to be repeated here?
       // Can't figure out why configuration gets lost without this in particular...
      _root_.coursier.ShadingPlugin.projectSettings ++
      Seq(
        shadingNamespace := "coursier.shaded",
        publish := publish.in(Shading).value,
        publishLocal := publishLocal.in(Shading).value,
        PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value,
        PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
      )
  
  lazy val proguardedArtifact = Def.setting {
    Artifact(
      moduleName.value,
      "jar",
      "jar",
      "standalone"
    )
  }

  lazy val proguardedJar = Def.task {

    val results = proguard.in(Proguard).value

    results match {
      case Seq(f) => f
      case Seq() =>
        throw new Exception("Found no proguarded files. Expected one.")
      case _ =>
        throw new Exception("Found several proguarded files. Don't know how to publish all of them.")
    }
  }

  lazy val addProguardedJar = {

    val extra = Def.taskDyn[Map[Artifact, File]] {
      if (scalaBinaryVersion.value == "2.11")
        Def.task(Map(proguardedArtifact.value -> proguardedJar.value))
      else
        Def.task(Map())
    }

    packagedArtifacts ++= extra.value
  }

  lazy val Integration = config("it").extend(Test)

}
