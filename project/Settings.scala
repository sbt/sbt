
import java.util.Locale

import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{scriptedBufferLog, scriptedLaunchOpts}

import com.jsuereth.sbtpgp._
import coursier.ShadingPlugin.autoImport.{Shading, shadingNamespace}

object Settings {

  def scala212 = "2.12.10"

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
  ) ++ {
    val prop = sys.props.getOrElse("publish.javadoc", "").toLowerCase(Locale.ROOT)
    if (prop == "0" || prop == "false")
      Seq(
        sources in (Compile, doc) := Seq.empty,
        publishArtifact in (Compile, packageDoc) := false
      )
    else
      Nil
  }

  lazy val plugin =
    shared ++
    Seq(
      // https://github.com/sbt/sbt/issues/5049#issuecomment-528960415
      dependencyOverrides := "org.scala-sbt" % "sbt" % "1.2.8" :: Nil,
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.name=" + name.value,
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath,
        "-Dcoursier.sbt-launcher.add-plugin=false"
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

  lazy val generatePropertyFile =
    resourceGenerators.in(Compile) += Def.task {
      import sys.process._

      val dir = classDirectory.in(Compile).value / "coursier"
      val ver = version.value

      val f = dir / "sbtcoursier.properties"
      dir.mkdirs()

      val p = new java.util.Properties

      p.setProperty("version", ver)
      p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

      val w = new java.io.FileOutputStream(f)
      p.store(w, "sbt-coursier properties")
      w.close()

      state.value.log.info(s"Wrote $f")

      Seq(f)
    }

}
