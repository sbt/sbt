
import java.util.Locale

import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{scriptedBufferLog, scriptedLaunchOpts}

import com.jsuereth.sbtpgp._

object Settings {

  def scala212 = "2.12.20"
  def scala213 = "2.13.15"
  def scala3 = "3.3.4"

  def targetSbtVersion = "1.2.8"

  private lazy val isAtLeastScala213 = Def.setting {
    import Ordering.Implicits._
    CrossVersion.partialVersion(scalaVersion.value).exists(_ >= (2, 13))
  }

  lazy val shared = Seq(
    resolvers ++= Resolver.sonatypeOssRepos("releases"),
    crossScalaVersions := Seq(scala212),
    scalaVersion := scala3,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-language:higherKinds",
      "-language:implicitConversions"
    ),
    libraryDependencies ++= {
      if (isAtLeastScala213.value) Nil
      else Seq(compilerPlugin("org.scalamacros" % s"paradise" % "2.1.1" cross CrossVersion.full))
    },
    scalacOptions ++= {
      if (isAtLeastScala213.value) Seq("-Ymacro-annotations")
      else Nil
    },
    libraryDependencySchemes ++= {
      val sv = scalaVersion.value
      if (sv.startsWith("2.13."))
        Seq("org.scala-lang.modules" %% "scala-xml" % "always")
      else
        Nil
    }
  ) ++ {
    val prop = sys.props.getOrElse("publish.javadoc", "").toLowerCase(Locale.ROOT)
    if (prop == "0" || prop == "false")
      Seq(
        Compile / doc / sources := Seq.empty,
        Compile / packageDoc / publishArtifact := false
      )
    else
      Nil
  }

  lazy val plugin =
    shared ++
    Seq(
      // https://github.com/sbt/sbt/issues/5049#issuecomment-528960415
      dependencyOverrides := "org.scala-sbt" % "sbt" % targetSbtVersion :: Nil,
      scriptedLaunchOpts ++= Seq(
        "-Xmx1024M",
        "-Dplugin.name=" + name.value,
        "-Dplugin.version=" + version.value,
        "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath,
        "-Dcoursier.sbt-launcher.add-plugin=false"
      ),
      scriptedBufferLog := false,
      sbtPlugin := true,
      pluginCrossBuild / sbtVersion := targetSbtVersion
    )

  lazy val generatePropertyFile =
    Compile / resourceGenerators += Def.task {
      import sys.process._

      val dir = (Compile / classDirectory).value / "coursier"
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

  lazy val dontPublish = Seq(
    publish := {},
    publish / skip := true,
  )

}
