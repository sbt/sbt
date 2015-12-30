import sbtrelease.ReleasePlugin.ReleaseKeys.{ publishArtifactsAction, versionBump }
import sbtrelease.Version.Bump

lazy val publishingSettings = Seq(
  publishMavenStyle := true,
  licenses := Seq("Apache 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/alexarchambault/coursier")),
  pomExtra := {
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
  }
) ++ releaseSettings

lazy val releaseSettings = sbtrelease.ReleasePlugin.releaseSettings ++ Seq(
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  versionBump := Bump.Bugfix,
  publishArtifactsAction := PgpKeys.publishSigned.value,
  credentials += {
    Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
      case Seq(Some(user), Some(pass)) =>
        Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
      case _ =>
        Credentials(Path.userHome / ".ivy2" / ".credentials")
    }
  }
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val baseCommonSettings = Seq(
  organization := "com.github.alexarchambault",
  resolvers ++= Seq(
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scalacOptions += "-target:jvm-1.7",
  javacOptions ++= Seq(
    "-source", "1.7",
    "-target", "1.7"
  )
)

lazy val commonSettings = baseCommonSettings ++ Seq(
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.6", "2.11.7"),
  libraryDependencies ++= {
    if (scalaVersion.value startsWith "2.10.")
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full))
    else
      Seq()
  }
)

lazy val core = crossProject
  .settings(commonSettings: _*)
  .settings(publishingSettings: _*)
  .settings(
    name := "coursier"
  )
  .jvmSettings(
    libraryDependencies ++=
      Seq(
        "org.scalaz" %% "scalaz-core" % "7.1.2"
      ) ++ {
        if (scalaVersion.value.startsWith("2.10.")) Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
        )
      }
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "com.github.japgolly.fork.scalaz" %%% "scalaz-core" % (if (scalaVersion.value.startsWith("2.10.")) "7.1.1" else "7.1.2"),
      "org.scala-js" %%% "scalajs-dom" % "0.8.0",
      "be.doeraene" %%% "scalajs-jquery" % "0.8.0"
    )
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val `fetch-js` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name := "coursier-fetch-js"
  )

lazy val tests = crossProject
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(noPublishSettings: _*)
  .settings(
    name := "coursier-tests",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-async" % "0.9.1" % "provided",
      "com.lihaoyi" %%% "utest" % "0.3.0" % "test"
    ),
    unmanagedResourceDirectories in Test += (baseDirectory in LocalRootProject).value / "tests" / "shared" / "src" / "test" / "resources",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
  .jsSettings(
    postLinkJSEnv := NodeJSEnv().value,
    scalaJSStage in Global := FastOptStage
  )

lazy val testsJvm = tests.jvm.dependsOn(files % "test")
lazy val testsJs = tests.js.dependsOn(`fetch-js` % "test")

lazy val files = project
  .dependsOn(coreJvm)
  .settings(commonSettings)
  .settings(publishingSettings)
  .settings(
    name := "coursier-files",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-concurrent" % "7.1.2"
    )
  )

lazy val bootstrap = project
  .settings(baseCommonSettings)
  .settings(publishingSettings)
  .settings(
    name := "coursier-bootstrap",
    artifactName := {
      val artifactName0 = artifactName.value
      (sv, m, artifact) =>
        if (artifact.`type` == "jar" && artifact.extension == "jar")
          "bootstrap.jar"
        else
          artifactName0(sv, m, artifact)
    },
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions in doc := Seq()
  )

lazy val cli = project
  .dependsOn(coreJvm, files)
  .settings(commonSettings)
  .settings(publishingSettings)
  .settings(packAutoSettings)
  .settings(
    name := "coursier-cli",
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "case-app" % "1.0.0-SNAPSHOT",
      "com.lihaoyi" %% "ammonite-terminal" % "0.5.0",
      "ch.qos.logback" % "logback-classic" % "1.1.3"
    )
  )

lazy val web = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs, `fetch-js`)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.10.")
        Seq()
      else
        Seq("com.github.japgolly.scalajs-react" %%% "core" % "0.9.0")
    },
    sourceDirectory := {
      val dir = sourceDirectory.value

      if (scalaVersion.value startsWith "2.10.")
        dir / "dummy"
      else
        dir
    },
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

lazy val doc = project
  .dependsOn(coreJvm, files)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(tutSettings)
  .settings(
    tutSourceDirectory := baseDirectory.value,
    tutTargetDirectory := baseDirectory.value / ".."
  )

// Don't try to compile that if you're not in 2.10
lazy val plugin = project
  .dependsOn(coreJvm, files, cli)
  .settings(baseCommonSettings)
  .settings(
    name := "coursier-sbt-plugin",
    sbtPlugin := true
  )

lazy val `coursier` = project.in(file("."))
  .aggregate(coreJvm, coreJs, `fetch-js`, testsJvm, testsJs, files, bootstrap, cli, web, doc)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
