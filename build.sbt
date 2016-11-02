import java.io.FileOutputStream

import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings

val binaryCompatibilityVersion = "1.0.0-M7"

lazy val IntegrationTest = config("it") extend Test

lazy val releaseSettings = Seq(
  publishMavenStyle := true,
  licenses := Seq("Apache 2.0" -> url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url("https://github.com/alexarchambault/coursier")),
  scmInfo := Some(ScmInfo(
    url("https://github.com/alexarchambault/coursier.git"),
    "scm:git:github.com/alexarchambault/coursier.git",
    Some("scm:git:git@github.com:alexarchambault/coursier.git")
  )),
  pomExtra := {
    <developers>
      <developer>
        <id>alexarchambault</id>
        <name>Alexandre Archambault</name>
        <url>https://github.com/alexarchambault</url>
      </developer>
    </developers>
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  credentials ++= {
    Seq("SONATYPE_USER", "SONATYPE_PASS").map(sys.env.get) match {
      case Seq(Some(user), Some(pass)) =>
        Seq(Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass))
      case _ =>
        Seq()
    }
  }
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

def noPublishForScalaVersionSettings(sbv: String*) = Seq(
  publish := {
    if (sbv.contains(scalaBinaryVersion.value))
      ()
    else
      publish.value
  },
  publishLocal := {
    if (sbv.contains(scalaBinaryVersion.value))
      ()
    else
      publishLocal.value
  },
  publishArtifact := {
    if (sbv.contains(scalaBinaryVersion.value))
      false
    else
      publishArtifact.value
  }
)

lazy val scalaVersionAgnosticCommonSettings = Seq(
  organization := "io.get-coursier",
  resolvers ++= Seq(
    "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
    Resolver.sonatypeRepo("releases")
  ),
  scalacOptions ++= {
    scalaBinaryVersion.value match {
      case "2.10" | "2.11" =>
        Seq("-target:jvm-1.7")
      case _ =>
        Seq()
    }
  },
  javacOptions ++= {
    scalaBinaryVersion.value match {
      case "2.10" | "2.11" =>
        Seq(
          "-source", "1.7",
          "-target", "1.7"
        )
      case _ =>
        Seq()
    }
  },
  javacOptions in Keys.doc := Seq()
) ++ releaseSettings

lazy val commonSettings = scalaVersionAgnosticCommonSettings ++ Seq(
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.12.0", "2.11.8", "2.10.6"),
  libraryDependencies ++= {
    if (scalaBinaryVersion.value == "2.10")
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
    else
      Seq()
  }
)

val scalazVersion = "7.2.7"

lazy val core = crossProject
  .settings(commonSettings: _*)
  .settings(mimaDefaultSettings: _*)
  .settings(
    name := "coursier",
    libraryDependencies ++= Seq(
      "org.scalaz" %%% "scalaz-core" % scalazVersion,
      "com.lihaoyi" %%% "fastparse" % "0.4.2"
    ),
    mimaPreviousArtifacts := {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Set("com.github.alexarchambault" %% moduleName.value % binaryCompatibilityVersion)
        case _ =>
          Set()
      }
    },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // Since 1.0.0-M13
        // reworked VersionConstraint
        ProblemFilters.exclude[MissingClassProblem]("coursier.core.VersionConstraint$Interval"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.core.VersionConstraint$Preferred"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.core.VersionConstraint$Preferred$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.core.VersionConstraint$Interval$"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.VersionConstraint"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.VersionConstraint.repr"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.core.VersionConstraint.this"),
        // Extra `actualVersion` field in `Project`
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Project$"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Project.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Project.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Project.this"),
        // Reworked Ivy pattern handling
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.pattern"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.properties"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.parts"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.substitute"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.substituteProperties"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.propertyRegex"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.apply"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.variableRegex"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.Pattern.optionalPartRegex"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart$Literal$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart$"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.ivy.IvyRepository.apply"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart$Optional$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart$Literal"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.ivy.Pattern$PatternPart$Optional"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.ivy.IvyRepository.pattern"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.ivy.IvyRepository.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.ivy.IvyRepository.properties"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.ivy.IvyRepository.metadataPattern"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.ivy.IvyRepository.this"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.util.Parse.repository"),
        // Since 1.0.0-M12
        // Extra `authentication` field
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Artifact.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Artifact.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.Artifact.this"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.ivy.IvyRepository$"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.ivy.IvyRepository.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.ivy.IvyRepository.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.ivy.IvyRepository.this"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenRepository.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenRepository.this"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenSource.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenRepository.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenSource.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.maven.MavenSource.this"),
        // Since 1.0.0-M11
        // Extra parameter with default value added, problem for forward compatibility only
        ProblemFilters.exclude[MissingMethodProblem]("coursier.core.ResolutionProcess.next"),
        // method made final (for - non critical - tail recursion)
        ProblemFilters.exclude[FinalMethodProblem]("coursier.core.ResolutionProcess.next"),
        // Since 1.0.0-M10
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.withParentConfigurations"),
        // New singleton object, problem for forward compatibility only
        ProblemFilters.exclude[MissingTypesProblem]("coursier.maven.MavenSource$")
      )
    }
  )
  .jvmSettings(
    libraryDependencies ++=
      Seq(
        "org.jsoup" % "jsoup" % "1.9.2"
      ) ++ {
        if (scalaBinaryVersion.value == "2.10") Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
        )
      },
    resourceGenerators.in(Compile) += {
      (target, version).map { (dir, ver) =>
        import sys.process._

        val f = dir / "coursier.properties"
        dir.mkdirs()

        val p = new java.util.Properties

        p.setProperty("version", ver)
        p.setProperty("commit-hash", Seq("git", "rev-parse", "HEAD").!!.trim)

        val w = new FileOutputStream(f)
        p.store(w, "Coursier properties")
        w.close()

        println(s"Wrote $f")

        Seq(f)
      }.taskValue
    }
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.1"
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
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(
    name := "coursier-tests",
    libraryDependencies += {
      val asyncVersion =
        if (scalaBinaryVersion.value == "2.10")
          "0.9.5"
        else
          "0.9.6"

        "org.scala-lang.modules" %% "scala-async" % asyncVersion % "provided"
    },
    libraryDependencies += "com.lihaoyi" %%% "utest" % "0.4.4" % "test",
    unmanagedResourceDirectories in Test += (baseDirectory in LocalRootProject).value / "tests" / "shared" / "src" / "test" / "resources",
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
  .jsSettings(
    scalaJSStage in Global := FastOptStage
  )

lazy val testsJvm = tests.jvm.dependsOn(cache % "test")
lazy val testsJs = tests.js.dependsOn(`fetch-js` % "test")

lazy val cache = project
  .dependsOn(coreJvm)
  .settings(commonSettings)
  .settings(mimaDefaultSettings)
  .settings(
    name := "coursier-cache",
    libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
    mimaPreviousArtifacts := {
      scalaBinaryVersion.value match {
        case "2.10" | "2.11" =>
          Set("com.github.alexarchambault" %% moduleName.value % binaryCompatibilityVersion)
        case _ =>
          Set()
      }
    },
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        // Since 1.0.0-M13
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache.file"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache.fetch"),
        // Since 1.0.0-M12
        // Remove deprecated / unused helper method
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache.readFully"),
        // Since 1.0.0-M11
        // Add constructor parameter on FileError - shouldn't be built by users anyway
        ProblemFilters.exclude[MissingMethodProblem]("coursier.FileError.this"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.FileError#Recoverable.this"),
        // Since 1.0.0-M10
        // methods that should have been private anyway
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay.update"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay.fallbackMode_="),
        // cache argument type changed from `Seq[(String, File)]` to `File`
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.Cache.file"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.Cache.fetch"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.Cache.default"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("coursier.Cache.validateChecksum"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache.defaultBase"),
        // New methdos in Cache.Logger
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache#Logger.checkingUpdates"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache#Logger.checkingUpdatesResult"),
        // Better overload of Cache.Logger.downloadLength, deprecate previous one
        ProblemFilters.exclude[MissingMethodProblem]("coursier.Cache#Logger.downloadLength"),
        // Changes to private class TermDisplay#Info
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Info$"),
        ProblemFilters.exclude[AbstractClassProblem]("coursier.TermDisplay$Info"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.downloaded"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.productElement"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.productArity"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.canEqual"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.length"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.display"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.fraction"),
        // Since 1.0.0-M9
        // Added an optional extra parameter to FileError.NotFound - only
        // its unapply method should break compatibility at the source level.
        ProblemFilters.exclude[MissingMethodProblem]("coursier.FileError#NotFound.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.FileError#NotFound.this"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.FileError$NotFound$"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.FileError#NotFound.apply"),
        // Since 1.0.0-M8
        ProblemFilters.exclude[MissingTypesProblem]("coursier.TermDisplay$Info$"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.apply"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.copy"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.pct"),
        ProblemFilters.exclude[MissingMethodProblem]("coursier.TermDisplay#Info.this")
      )
    }
  )

lazy val bootstrap = project
  .settings(scalaVersionAgnosticCommonSettings)
  .settings(noPublishSettings)
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
    autoScalaLibrary := false
  )

lazy val cli = project
  .dependsOn(coreJvm, cache)
  .settings(commonSettings)
  .settings(noPublishForScalaVersionSettings("2.10", "2.12"))
  .settings(packAutoSettings)
  .settings(proguardSettings)
  .settings(
    name := "coursier-cli",
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq("com.github.alexarchambault" %% "case-app" % "1.1.2")
      else
        Seq()
    },
    resourceGenerators in Compile += packageBin.in(bootstrap).in(Compile).map { jar =>
      Seq(jar)
    }.taskValue,
    ProguardKeys.proguardVersion in Proguard := "5.3",
    ProguardKeys.options in Proguard ++= Seq(
      "-dontwarn",
      "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
      "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}"
    ),
    javaOptions in (Proguard, ProguardKeys.proguard) := Seq("-Xmx3172M"),
    artifactPath in Proguard := (ProguardKeys.proguardDirectory in Proguard).value / "coursier-standalone.jar",
    artifacts ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          Artifact(
            moduleName.value,
            "jar",
            "jar",
            "standalone"
          )
        )
      else
        Nil
    },
    packagedArtifacts <++= {
      (
        moduleName,
        scalaBinaryVersion,
        ProguardKeys.proguard in Proguard,
        packageBin.in(bootstrap) in Compile
      ).map {
        (mod, sbv, files, bootstrapJar) =>
          if (sbv == "2.11") {
            import java.util.zip.{ ZipEntry, ZipOutputStream, ZipInputStream }
            import java.io.{ ByteArrayOutputStream, FileInputStream, FileOutputStream, File, InputStream, IOException }

            val f0 = files match {
              case Seq(f) => f
              case Seq() =>
                throw new Exception("Found no proguarded files. Expected one.")
              case _ =>
                throw new Exception("Found several proguarded files. Don't know how to publish all of them.")
            }

            val f = new File(f0.getParentFile, f0.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

            val is = new FileInputStream(f0)
            val os = new FileOutputStream(f)
            val bootstrapZip = new ZipInputStream(is)
            val outputZip = new ZipOutputStream(os)

            def readFullySync(is: InputStream) = {
              val buffer = new ByteArrayOutputStream
              val data = Array.ofDim[Byte](16384)

              var nRead = is.read(data, 0, data.length)
              while (nRead != -1) {
                buffer.write(data, 0, nRead)
                nRead = is.read(data, 0, data.length)
              }

              buffer.flush()
              buffer.toByteArray
            }

            def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
              new Iterator[(ZipEntry, Array[Byte])] {
                var nextEntry = Option.empty[ZipEntry]
                def update() =
                  nextEntry = Option(zipStream.getNextEntry)

                update()

                def hasNext = nextEntry.nonEmpty
                def next() = {
                  val ent = nextEntry.get
                  val data = readFullySync(zipStream)

                  update()

                  (ent, data)
                }
              }

            for ((ent, data) <- zipEntries(bootstrapZip)) {
              outputZip.putNextEntry(ent)
              outputZip.write(data)
              outputZip.closeEntry()
            }

            outputZip.putNextEntry(new ZipEntry("bootstrap.jar"))
            outputZip.write(readFullySync(new FileInputStream(bootstrapJar)))
            outputZip.closeEntry()

            outputZip.close()

            is.close()
            os.close()


            Map(
              // FIXME Same Artifact as above
              Artifact(
                mod,
                "jar",
                "jar",
                "standalone"
              ) -> f
            )
          } else
            Map.empty[Artifact, File]
      }
    }
  )

lazy val web = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs, `fetch-js`)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          "be.doeraene" %%% "scalajs-jquery" % "0.9.0",
          "com.github.japgolly.scalajs-react" %%% "core" % "0.9.0"
        )
      else
        Seq()
    },
    sourceDirectory := {
      val dir = sourceDirectory.value

      if (scalaBinaryVersion.value == "2.11")
        dir
      else
        dir / "dummy"
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
  .dependsOn(coreJvm, cache)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(tutSettings)
  .settings(
    tutSourceDirectory := baseDirectory.value,
    tutTargetDirectory := baseDirectory.value / ".."
  )

// Don't try to compile that if you're not in 2.10
lazy val plugin = project
  .dependsOn(coreJvm, cache)
  .settings(scalaVersionAgnosticCommonSettings)
  .settings(noPublishForScalaVersionSettings("2.11", "2.12"))
  .settings(
    name := "sbt-coursier",
    sbtPlugin := (scalaBinaryVersion.value == "2.10"),
    resolvers ++= Seq(
      // added so that 2.10 artifacts of the other modules can be found by
      // the too-naive-for-now inter-project resolver of the coursier SBT plugin
      Resolver.sonatypeRepo("snapshots"),
      // added for sbt-scripted to be fine even with ++2.11.x
      Resolver.typesafeIvyRepo("releases")
    )
  )
  .settings(ScriptedPlugin.scriptedSettings)
  .settings(
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-XX:MaxPermSize=256M",
      "-Dplugin.version=" + version.value,
      "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
    ),
    scriptedBufferLog := false
  )

val http4sVersion = "0.8.6"

lazy val `http-server` = project
  .settings(commonSettings)
  .settings(packAutoSettings)
  .settings(noPublishForScalaVersionSettings("2.10", "2.12"))
  .settings(
    name := "http-server-java7",
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          "org.http4s" %% "http4s-blazeserver" % http4sVersion,
          "org.http4s" %% "http4s-dsl" % http4sVersion,
          "org.slf4j" % "slf4j-nop" % "1.7.21",
          "com.github.alexarchambault" %% "case-app" % "1.1.2"
        )
      else
        Seq()
    }
  )

lazy val okhttp = project
  .dependsOn(cache)
  .settings(commonSettings)
  .settings(
    name := "coursier-okhttp",
    libraryDependencies ++= Seq(
      "com.squareup.okhttp" % "okhttp-urlconnection" % "2.7.5"
    )
  )

lazy val `coursier` = project.in(file("."))
  .aggregate(coreJvm, coreJs, `fetch-js`, testsJvm, testsJs, cache, bootstrap, cli, plugin, web, doc, `http-server`, okhttp)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "coursier-root"
  )
