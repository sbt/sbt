import java.io.FileOutputStream

val binaryCompatibilityVersion = "1.0.0-M14"
val binaryCompatibility212Version = "1.0.0-M15"

parallelExecution in Global := false

lazy val IntegrationTest = config("it") extend Test

lazy val scalazVersion = "7.2.8"

lazy val core = crossProject
  .settings(commonSettings)
  .settings(mimaPreviousArtifactSettings)
  .jvmConfigure(_
    .enablePlugins(_root_.coursier.ShadingPlugin)
  )
  .jvmSettings(
    shadingNamespace := "coursier.shaded",
    libraryDependencies += "com.lihaoyi" %% "fastparse" % "0.4.2" % "shaded",
    publish := publish.in(Shading).value,
    publishLocal := publishLocal.in(Shading).value
  )
  .jsSettings(
    libraryDependencies += "com.lihaoyi" %%% "fastparse" % "0.4.2"
  )
  .settings(
    name := "coursier",
    libraryDependencies ++= Seq(
      "org.scalaz" %%% "scalaz-core" % scalazVersion
    ),
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.defaultPublications"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.defaultPackaging"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.maven.MavenSource$DocSourcesArtifactExtensions"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageDirectoryElements"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageSubDirectories"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageFiles"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Project$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.apply"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Project.copy$default$13"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Project.copy$default$12"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.copy$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.packagingBlacklist"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.apply$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.ignorePackaging"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.<init>$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.apply"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Activation$Os"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Version"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Authentication"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.VersionInterval"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Opt"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Const"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Opt"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Var"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.IvyRepository"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Const"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Prop"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Var"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.maven.MavenRepository"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.maven.MavenSource"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.package#Resolution.apply$default$9"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.package#Resolution.apply"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.copy$default$9"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.copyWithCache$default$8"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.profileActivation"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.copyWithCache"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.this"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Activation$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.apply"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.profiles"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.apply")
      )
    }
  )
  .jvmSettings(
    libraryDependencies ++=
      Seq(
        "org.jsoup" % "jsoup" % "1.10.2"
      ) ++ {
        if (scalaBinaryVersion.value == "2.10") Seq()
        else Seq(
          "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
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
  .settings(commonSettings)
  .settings(noPublishSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
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
    libraryDependencies += "com.lihaoyi" %%% "utest" % "0.4.5" % "test",
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
  .settings(mimaPreviousArtifactSettings)
  .settings(
    name := "coursier-cache",
    libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.TermDisplay#UpdateDisplayRunnable.cleanDisplay"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.TermDisplay$DownloadInfo"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.TermDisplay$CheckUpdateInfo"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.util.Base64$B64Scheme"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$Stop$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$Update$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$UpdateDisplayThread")
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
        Seq("com.github.alexarchambault" %% "case-app" % "1.1.3")
      else
        Seq()
    },
    packExcludeArtifactTypes += "pom",
    resourceGenerators in Compile += packageBin.in(bootstrap).in(Compile).map { jar =>
      Seq(jar)
    }.taskValue,
    ProguardKeys.proguardVersion in Proguard := "5.3",
    ProguardKeys.options in Proguard ++= Seq(
      "-dontwarn",
      "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
      "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
      "-adaptresourcefilenames **.properties"
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
          "be.doeraene" %%% "scalajs-jquery" % "0.9.1",
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
lazy val `sbt-coursier` = project
  .dependsOn(coreJvm, cache)
  .settings(pluginSettings)

// Don't try to compile that if you're not in 2.10
lazy val `sbt-shading` = project
  .enablePlugins(_root_.coursier.ShadingPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(pluginSettings)
  .settings(
    shadingNamespace := "coursier.shaded",
    resolvers += Resolver.mavenLocal,
    libraryDependencies += {
      val coursierJarjarVersion = "1.0.1-coursier-SNAPSHOT"
      def coursierJarjarFoundInM2 = (file(sys.props("user.home")) / s".m2/repository/org/anarres/jarjar/jarjar-core/$coursierJarjarVersion").exists()

      val jarjarVersion =
        if (sys.env.contains("CI") || coursierJarjarFoundInM2 || !isSnapshot.value)
          coursierJarjarVersion
        else {
          val fallback = "1.0.0"

          // streams.value.log.warn( // "a setting cannot depend on a task"
          scala.Console.err.println(
           s"""Warning: using jarjar $fallback, which doesn't properly shade Scala JARs (classes with '$$' aren't shaded).
              |See the instructions around
              |https://github.com/alexarchambault/coursier/blob/630a780487d662dd994ed1c3246895a22c00cf21/scripts/travis.sh#L40
              |to use a version fine with Scala JARs.""".stripMargin
          )

          fallback
        }

      "org.anarres.jarjar" % "jarjar-core" % jarjarVersion % "shaded"
    },
    publish := publish.in(Shading).value,
    publishLocal := publishLocal.in(Shading).value
  )

lazy val `sbt-launcher` = project
  .dependsOn(cache)
  .settings(commonSettings)
  .settings(packAutoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.alexarchambault" %% "case-app" % "1.1.3",
      "org.scala-sbt" % "launcher-interface" % "1.0.0",
      "com.typesafe" % "config" % "1.3.1"
    ),
    packExcludeArtifactTypes += "pom"
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
          "org.slf4j" % "slf4j-nop" % "1.7.22",
          "com.github.alexarchambault" %% "case-app" % "1.1.3"
        )
      else
        Seq()
    },
    packExcludeArtifactTypes += "pom"
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

lazy val jvm = project
  .aggregate(
    coreJvm,
    testsJvm,
    cache,
    bootstrap,
    cli,
    `sbt-coursier`,
    `sbt-shading`,
    `sbt-launcher`,
    doc,
    `http-server`,
    okhttp
  )
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "coursier-jvm"
  )

lazy val js = project
  .aggregate(
    coreJs,
    `fetch-js`,
    testsJs,
    web
  )
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "coursier-js"
  )

lazy val `coursier` = project.in(file("."))
  .aggregate(
    coreJvm,
    coreJs,
    `fetch-js`,
    testsJvm,
    testsJs,
    cache,
    bootstrap,
    cli,
    `sbt-coursier`,
    `sbt-shading`,
    `sbt-launcher`,
    web,
    doc,
    `http-server`,
    okhttp
  )
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(releaseSettings)
  .settings(
    moduleName := "coursier-root"
  )

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
        Seq("-target:jvm-1.6")
      case _ =>
        Seq()
    }
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
  javacOptions in Keys.doc := Seq()
) ++ releaseSettings

lazy val commonSettings = scalaVersionAgnosticCommonSettings ++ Seq(
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.12.1", "2.11.8", "2.10.6"),
  libraryDependencies ++= {
    if (scalaBinaryVersion.value == "2.10")
      Seq(compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
    else
      Seq()
  }
)

lazy val pluginSettings =
  scalaVersionAgnosticCommonSettings ++
  noPublishForScalaVersionSettings("2.11", "2.12") ++
  ScriptedPlugin.scriptedSettings ++
  Seq(
    scriptedLaunchOpts ++= Seq(
      "-Xmx1024M",
      "-XX:MaxPermSize=256M",
      "-Dplugin.version=" + version.value,
      "-Dsbttest.base=" + (sourceDirectory.value / "sbt-test").getAbsolutePath
    ),
    scriptedBufferLog := false,
    sbtPlugin := (scalaBinaryVersion.value == "2.10"),
    resolvers ++= Seq(
      // added so that 2.10 artifacts of the other modules can be found by
      // the too-naive-for-now inter-project resolver of the coursier SBT plugin
      Resolver.sonatypeRepo("snapshots"),
      // added for sbt-scripted to be fine even with ++2.11.x
      Resolver.typesafeIvyRepo("releases")
    )
  )

lazy val mimaPreviousArtifactSettings = Seq(
  mimaPreviousArtifacts := {
    val version = scalaBinaryVersion.value match {
      case "2.12" => binaryCompatibility212Version
      case _ => binaryCompatibilityVersion
    }

    Set(organization.value %% moduleName.value % version)
  }
)
