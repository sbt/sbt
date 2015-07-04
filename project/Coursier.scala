import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

import sbt._, Keys._

import sbtrelease.ReleasePlugin.releaseSettings
import sbtrelease.ReleasePlugin.ReleaseKeys.{ publishArtifactsAction, versionBump }
import sbtrelease.Version.Bump
import com.typesafe.sbt.pgp.PgpKeys

import xerial.sbt.Pack._


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
    organization := "com.github.alexarchambault",
    scalaVersion := "2.11.6",
    crossScalaVersions := Seq("2.10.5", "2.11.6"),
    resolvers ++= Seq(
      "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
      Resolver.sonatypeRepo("releases")
    )
  ) ++ publishingSettings

  private lazy val commonCoreSettings = commonSettings ++ Seq[Setting[_]](
    name := "coursier",
    libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
    unmanagedSourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "core" / "src" / "main" / "scala",
    unmanagedSourceDirectories in Test += (baseDirectory in LocalRootProject).value / "core" / "src" / "test" / "scala",
    unmanagedResourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "core" / "src" / "main" / "resources",
    unmanagedResourceDirectories in Test += (baseDirectory in LocalRootProject).value / "core" / "src" / "test" / "resources",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

  lazy val coreJvm = Project(id = "core-jvm", base = file("core-jvm"))
    .settings(commonCoreSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "org.scalaz" %% "scalaz-concurrent" % "7.1.2",
        "com.lihaoyi" %% "utest" % "0.3.0" % "test"
      ) ++ {
        if (scalaVersion.value.startsWith("2.10.")) Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
        )
      }
    )

  lazy val coreJs = Project(id = "core-js", base = file("core-js"))
    .settings(commonCoreSettings: _*)
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

  lazy val files = Project(id = "files", base = file("files"))
    .dependsOn(coreJvm)
    .settings(commonSettings: _*)
    .settings(
      name := "coursier-files",
      libraryDependencies ++= Seq(
        // "org.http4s" %% "http4s-blazeclient" % "0.8.2",
        "com.lihaoyi" %% "utest" % "0.3.0" % "test"
      ),
      testFrameworks += new TestFramework("utest.runner.Framework")
    )

  lazy val cli = Project(id = "cli", base = file("cli"))
    .dependsOn(coreJvm, files)
    .settings(commonSettings ++ packAutoSettings ++ publishPackTxzArchive ++ publishPackZipArchive: _*)
    .settings(
      packArchivePrefix := s"coursier-cli_${scalaBinaryVersion.value}",
      packArchiveTxzArtifact := Artifact("coursier-cli", "arch", "tar.xz"),
      packArchiveZipArtifact := Artifact("coursier-cli", "arch", "zip")
    )
    .settings(
      name := "coursier-cli",
      libraryDependencies ++= Seq(
        "com.github.alexarchambault" %% "case-app" % "0.3.0",
        "ch.qos.logback" % "logback-classic" % "1.1.3"
      ) ++ {
        if (scalaVersion.value startsWith "2.10.")
          Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
        else
          Seq()
      }
    )

  lazy val web = Project(id = "web", base = file("web"))
    .dependsOn(coreJs)
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= {
        if (scalaVersion.value startsWith "2.10.")
          Seq()
        else
          Seq(
            "com.github.japgolly.scalajs-react" %%% "core" % "0.9.0"
          )
      },
      sourceDirectory := {
        val dir = sourceDirectory.value

        if (scalaVersion.value startsWith "2.10.")
          dir / "dummy"
        else
          dir
      },
      publish := (),
      publishLocal := (),
      test in Test := (),
      testOnly in Test := (),
      resolvers += "Webjars Bintray" at "https://dl.bintray.com/webjars/maven/",
      jsDependencies ++= Seq(
        ("org.webjars.bower" % "bootstrap" % "3.3.4" intransitive()) / "bootstrap.min.js" commonJSName "Bootstrap",
        ("org.webjars.bower" % "react" % "0.12.2" intransitive()) / "react-with-addons.js" commonJSName "React",
        ("org.webjars.bower" % "bootstrap-treeview" % "1.2.0" intransitive()) / "bootstrap-treeview.min.js" commonJSName "Treeview",
        ("org.webjars.bower" % "raphael" % "2.1.4" intransitive()) / "raphael-min.js" commonJSName "Raphael"
      )
    )
    .enablePlugins(ScalaJSPlugin)

  lazy val root = Project(id = "root", base = file("."))
    .aggregate(coreJvm, coreJs, files, cli, web)
    .settings(commonSettings: _*)
    .settings(
      (unmanagedSourceDirectories in Compile) := Nil,
      (unmanagedSourceDirectories in Test) := Nil,
      publish := (),
      publishLocal := ()
    )

}
