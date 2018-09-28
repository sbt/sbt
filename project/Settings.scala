
import java.nio.file.Files

import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{sbtLauncher, scriptedBufferLog, ScriptedLaunchConf, scriptedLaunchOpts}

import com.typesafe.sbt.pgp._
import coursier.ShadingPlugin.autoImport._

import Aliases._

object Settings {

  def scala212 = "2.12.7"

  def sonatypeRepository(name: String) = {
    resolvers += Resolver.sonatypeRepo(name)
  }

  lazy val shared = Publish.released ++ Seq(
    organization := "io.get-coursier",
    sonatypeRepository("releases"),
    crossScalaVersions := Seq(scala212),
    scalaVersion := scala212,
    scalacOptions ++= Seq(
      "-target:jvm-1.8",
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-language:implicitConversions"
    ),
    javacOptions ++= Seq(
      "-source", "1.8",
      "-target", "1.8"
    ),
    javacOptions.in(Keys.doc) := Seq()
  )

  lazy val utest = Seq(
    libs += Deps.utest % Test,
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  def sbt10Version = "1.0.2"

  lazy val plugin =
    shared ++
    withScriptedTests ++
    Seq(
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
      ),
      scriptedBufferLog := false,
      sbtPlugin := true,
      sbtVersion.in(pluginCrossBuild) := sbt10Version
    )

  lazy val shading =
    inConfig(_root_.coursier.ShadingPlugin.Shading)(PgpSettings.projectSettings) ++
       // Why does this have to be repeated here?
       // Can't figure out why configuration gets lost without this in particular...
      _root_.coursier.ShadingPlugin.projectSettings ++
      Seq(
        shadingNamespace := "coursier.shaded",
        publish := publish.in(Shading).value,
        publishLocal := publishLocal.in(Shading).value,
        PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value,
        PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
      )
  
}
