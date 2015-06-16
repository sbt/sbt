import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import sbt._, Keys._

import sbtrelease.ReleasePlugin.releaseSettings
import sbtrelease.ReleasePlugin.ReleaseKeys.{ publishArtifactsAction, versionBump }
import sbtrelease.Version.Bump
import com.typesafe.sbt.pgp.PgpKeys

object CoursierBuild extends Build {

  lazy val publishingSettings = Seq[Setting[_]](
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    pomExtra := {
      <url>https://github.com/alexarchambault/coursier</url>
      <licenses>
        <license>
          <name>Apache 2.0</name>
          <url>http://opensource.org/licenses/Apache-2.0</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/alexarchambault/coursier.git</connection>
        <developerConnection>scm:git:git@github.com:alexarchambault/coursier.git</developerConnection>
        <url>github.com/alexarchambault/coursier.git</url>
      </scm>
      <developers>
        <developer>
          <id>alexarchambault</id>
          <name>Alexandre Archambault</name>
          <url>https://github.com/alexarchambault</url>
        </developer>
      </developers>
    },
    credentials += {
      Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
        case Seq(Some(user), Some(pass)) =>
          Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
        case _ =>
          Credentials(Path.userHome / ".ivy2" / ".credentials")
      }
    },
    versionBump := Bump.Bugfix,
    publishArtifactsAction := PgpKeys.publishSigned.value
  ) ++ releaseSettings

  lazy val commonSettings = Seq[Setting[_]](
    organization := "eu.frowning-lambda",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.10.5", "2.11.6"),
    resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
  ) ++ publishingSettings

  private lazy val commonCoreSettings = Seq[Setting[_]](
    name := "coursier",
    libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
    unmanagedSourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "core" / "src" / "main" / "scala",
    unmanagedSourceDirectories in Test += (baseDirectory in LocalRootProject).value / "core" / "src" / "test" / "scala",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  lazy val coreJvm = Project(id = "core-jvm", base = file("core-jvm"))
    .settings(commonSettings ++ commonCoreSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-blazeclient" % "0.7.0",
        "com.lihaoyi" %% "utest" % "0.3.0" % "test"
      ) ++ {
        if (scalaVersion.value.startsWith("2.10.")) Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
        )
      }
    )

  lazy val coreJs = Project(id = "core-js", base = file("core-js"))
    .settings(commonSettings ++ commonCoreSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "0.8.0",
        "com.github.japgolly.fork.scalaz" %%% "scalaz-core" % (if (scalaVersion.value.startsWith("2.10.")) "7.1.1" else "7.1.2"),
        "be.doeraene" %%% "scalajs-jquery" % "0.8.0",
        "com.lihaoyi" %%% "utest" % "0.3.0" % "test"
      ),
      postLinkJSEnv := NodeJSEnv().value,
      scalaJSStage in Global := FastOptStage
    )
    .enablePlugins(ScalaJSPlugin)

  lazy val cli = Project(id = "cli", base = file("cli"))
    .dependsOn(coreJvm)
    .settings(commonSettings ++ xerial.sbt.Pack.packAutoSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.github.alexarchambault" %% "case-app" % "0.2.2",
        "ch.qos.logback" % "logback-classic" % "1.1.3"
      )
    )

  lazy val web = Project(id = "web", base = file("web"))
    .dependsOn(coreJs)
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "com.github.japgolly.scalajs-react" %%% "core" % "0.9.0"
      ),
      jsDependencies +=
        "org.webjars" % "react" % "0.12.2" / "react-with-addons.js" commonJSName "React"
    )
    .enablePlugins(ScalaJSPlugin)

}
