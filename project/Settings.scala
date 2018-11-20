
import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{scriptedBufferLog, scriptedLaunchOpts}

import com.typesafe.sbt.pgp._
import coursier.ShadingPlugin.autoImport.{Shading, shadingNamespace}

object Settings {

  def scala212 = "2.12.7"

  def sbt10Version = "1.0.2"

  lazy val shared = Seq(
    resolvers += Resolver.sonatypeRepo("releases"),
    crossScalaVersions := Seq(scala212),
    scalaVersion := scala212,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-language:implicitConversions"
    )
  )

  lazy val plugin =
    shared ++
    Seq(
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.name=" + name.value,
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
      ),
      scriptedBufferLog := false,
      sbtPlugin := true,
      sbtVersion.in(pluginCrossBuild) := sbt10Version
    )

  lazy val shading =
    inConfig(Shading)(PgpSettings.projectSettings) ++
       // Why does this have to be repeated here?
       // Can't figure out why configuration gets lost without this in particular...
      coursier.ShadingPlugin.projectSettings ++
      Seq(
        shadingNamespace := "coursier.shaded",
        publish := publish.in(Shading).value,
        publishLocal := publishLocal.in(Shading).value,
        PgpKeys.publishSigned := PgpKeys.publishSigned.in(Shading).value,
        PgpKeys.publishLocalSigned := PgpKeys.publishLocalSigned.in(Shading).value
      )
  
}
