
import java.util.Locale

import sbt._
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{scriptedBufferLog, scriptedLaunchOpts}
import sbtcompatibility.SbtCompatibilityPlugin.autoImport._
import sbtevictionrules.EvictionRulesPlugin.autoImport._

import com.jsuereth.sbtpgp._

object Settings {

  def scala212 = "2.12.13"
  def scala213 = "2.13.5"

  def targetSbtVersion = "1.2.8"

  private lazy val isAtLeastScala213 = Def.setting {
    import Ordering.Implicits._
    CrossVersion.partialVersion(scalaVersion.value).exists(_ >= (2, 13))
  }

  lazy val shared = Seq(
    resolvers += Resolver.sonatypeRepo("releases"),
    crossScalaVersions := Seq(scala212),
    scalaVersion := scala212,
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
    compatibilityRules ++= Seq(
      "com.eed3si9n" %% "gigahorse-*" % "semver",
      "org.scala-lang.modules" % "*" % "semver",
      "org.scala-sbt" % "*" % "semver",
      "com.typesafe" %% "ssl-config-core" % "semver",
      "net.java.dev.jna" % "jna*" % "always"
    ),
    compatibilityIgnored += "com.swoval" % "apple-file-events",
    evictionRules ++= Seq(
      "com.eed3si9n" %% "gigahorse-*" % "semver",
      "org.scala-lang.modules" %% "*" % "semver"
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
      sbtVersion.in(pluginCrossBuild) := targetSbtVersion
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
