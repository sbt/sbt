
import Aliases._
import Settings._
import Publish._

parallelExecution.in(Global) := false

lazy val core = crossProject
  .disablePlugins(ScriptedPlugin)
  .jvmConfigure(_.enablePlugins(ShadingPlugin))
  .jvmSettings(
    shading,
    quasiQuotesIfNecessary,
    scalaXmlIfNecessary,
    libs ++= Seq(
      Deps.fastParse % "shaded",
      Deps.jsoup % "shaded"
    ),
    shadeNamespaces ++= Set(
      "org.jsoup",
      "fastparse",
      "sourcecode"
    ),
    generatePropertyFile
  )
  .jsSettings(
    libs ++= Seq(
      CrossDeps.fastParse.value,
      CrossDeps.scalaJsDom.value
    )
  )
  .settings(
    shared,
    name := "coursier",
    libs += CrossDeps.scalazCore.value,
    Mima.previousArtifacts,
    Mima.coreFilters
  )

lazy val coreJvm = core.jvm
lazy val coreJs = core.js

lazy val `fetch-js` = project
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs)
  .settings(
    shared,
    dontPublish,
    coursierPrefix
  )

lazy val tests = crossProject
  .disablePlugins(ScriptedPlugin)
  .dependsOn(core)
  .jvmConfigure(_.dependsOn(cache % "test"))
  .jsConfigure(_.dependsOn(`fetch-js` % "test"))
  .jsSettings(
    scalaJSStage.in(Global) := FastOptStage
  )
  .configs(Integration)
  .settings(
    shared,
    dontPublish,
    hasITs,
    coursierPrefix,
    libs += Deps.scalaAsync.value,
    utest,
    sharedTestResources
  )

lazy val testsJvm = tests.jvm
lazy val testsJs = tests.js

lazy val `proxy-tests` = project
  .disablePlugins(ScriptedPlugin)
  .dependsOn(testsJvm % "test->test")
  .configs(Integration)
  .settings(
    shared,
    dontPublish,
    hasITs,
    coursierPrefix,
    libs += Deps.scalaAsync.value,
    utest,
    sharedTestResources
  )

lazy val paths = project
  .disablePlugins(ScriptedPlugin)
  .settings(
    pureJava,
    dontPublish,
    addDirectoriesSources
  )

lazy val cache = project
  .disablePlugins(ScriptedPlugin)
  .dependsOn(coreJvm)
  .settings(
    shared,
    Mima.previousArtifacts,
    coursierPrefix,
    libs += Deps.scalazConcurrent,
    Mima.cacheFilters,
    addPathsSources
  )

lazy val bootstrap = project
  .disablePlugins(ScriptedPlugin)
  .settings(
    pureJava,
    dontPublish,
    addPathsSources,
    // seems not to be automatically found with sbt 0.13.16-M1 :-/
    mainClass := Some("coursier.Bootstrap"),
    renameMainJar("bootstrap.jar")
  )

lazy val extra = project
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(ShadingPlugin)
  .dependsOn(coreJvm)
  .settings(
    shared,
    coursierPrefix,
    shading,
    libs ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          Deps.scalaNativeTools % "shaded",
          // brought by only tools, so should be automaticaly shaded,
	  // but issues in ShadingPlugin (with things published locally?)
	  // seem to require explicit shading...
          Deps.scalaNativeNir % "shaded",
          Deps.scalaNativeUtil % "shaded",
          Deps.fastParse % "shaded"
        )
      else
        Nil
    },
    shadeNamespaces ++=
      Set(
        "fastparse",
        "sourcecode"
      ) ++
      // not blindly shading the whole scala.scalanative here, for some constant strings starting with
      // "scala.scalanative.native." in scalanative not to get prefixed with "coursier.shaded."
      Seq("codegen", "io", "linker", "nir", "optimizer", "tools", "util")
        .map("scala.scalanative." + _)
  )

lazy val cli = project
  .dependsOn(coreJvm, cache, extra)
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(PackPlugin, SbtProguard)
  .settings(
    shared,
    dontPublishIn("2.10", "2.12"),
    coursierPrefix,
    libs ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          Deps.caseApp,
          Deps.argonautShapeless,
          Deps.junit % "test", // to be able to run tests with pants
          Deps.scalatest % "test"
        )
      else
        Seq()
    },
    addBootstrapJarAsResource,
    proguardedCli
  )

lazy val web = project
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs, `fetch-js`)
  .settings(
    shared,
    dontPublish,
    libs ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          CrossDeps.scalaJsJquery.value,
          CrossDeps.scalaJsReact.value
        )
      else
        Seq()
    },
    sourceDirectory := {
      val dir = sourceDirectory.value

      if (scalaBinaryVersion.value == "2.11")
        dir
      else
        dir / "target" / "dummy"
    },
    noTests,
    webjarBintrayRepository,
    jsDependencies ++= Seq(
      WebDeps.bootstrap
        .intransitive()
        ./("bootstrap.min.js")
        .commonJSName("Bootstrap"),
      WebDeps.react
        .intransitive()
        ./("react-with-addons.js")
        .commonJSName("React"),
      WebDeps.bootstrapTreeView
        .intransitive()
        ./("bootstrap-treeview.min.js")
        .commonJSName("Treeview"),
      WebDeps.raphael
        .intransitive()
        ./("raphael-min.js")
        .commonJSName("Raphael")
    )
  )

lazy val readme = project
  .in(file("doc/readme"))
  .dependsOn(coreJvm, cache)
  .disablePlugins(ScriptedPlugin)
  .enablePlugins(TutPlugin)
  .settings(
    shared,
    dontPublish,
    tutSourceDirectory := baseDirectory.value,
    tutTargetDirectory := baseDirectory.in(LocalRootProject).value
  )

lazy val `sbt-shared` = project
  .dependsOn(coreJvm, cache)
  .disablePlugins(ScriptedPlugin)
  .settings(
    plugin,
    utest,
//  addSbtPlugin("com.dwijnand" % "sbt-compat" % "1.2.0+2-f30b82f4")
    libs ++= {
      val dependency = "com.dwijnand" % "sbt-compat" % "1.2.0+2-f30b82f4"
      val sbtV = (sbtBinaryVersion in pluginCrossBuild).value
      val scalaV = (scalaBinaryVersion in update).value
      val m = Defaults.sbtPluginExtra(dependency, sbtV, scalaV)
      CrossVersion.partialVersion(scalaVersion.value).collect {
        case (2, 10) => m
        case (2, 12) => m
      }.toList
    }
  )

lazy val `sbt-coursier` = project
  .dependsOn(coreJvm, cache, extra, `sbt-shared`)
  .disablePlugins(ScriptedPlugin)
  .settings(
    plugin,
    utest
  )

lazy val `sbt-pgp-coursier` = project
  .dependsOn(`sbt-coursier`)
  .disablePlugins(ScriptedPlugin)
  .settings(
    plugin,
    libs ++= {
      scalaBinaryVersion.value match {
        case "2.10" | "2.12" =>
          Seq(Deps.sbtPgp.value)
        case _ => Nil
      }
    }
  )

lazy val `sbt-shading` = project
  .enablePlugins(ShadingPlugin)
  .disablePlugins(ScriptedPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    shading,
    localM2Repository, // for a possibly locally published jarjar
    libs += Deps.jarjar % "shaded",
    // dependencies of jarjar-core - directly depending on these so that they don't get shaded
    libs ++= Deps.jarjarTransitiveDeps
  )

lazy val okhttp = project
  .dependsOn(cache)
  .disablePlugins(ScriptedPlugin)
  .settings(
    shared,
    coursierPrefix,
    libs += Deps.okhttpUrlConnection
  )

lazy val jvm = project
  .dummy
  .disablePlugins(ScriptedPlugin)
  .aggregate(
    coreJvm,
    testsJvm,
    `proxy-tests`,
    paths,
    cache,
    bootstrap,
    extra,
    cli,
    `sbt-shared`,
    `sbt-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`,
    readme,
    okhttp
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-jvm"
  )

lazy val js = project
  .dummy
  .disablePlugins(ScriptedPlugin)
  .aggregate(
    coreJs,
    `fetch-js`,
    testsJs,
    web
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-js"
  )

// run sbt-plugins/publishLocal to publish all that necessary for plugins
lazy val `sbt-plugins` = project
  .dummy
  .disablePlugins(ScriptedPlugin)
  .aggregate(
    coreJvm,
    cache,
    extra,
    `sbt-shared`,
    `sbt-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`
  )
  .settings(
    shared,
    pluginOverrideCrossScalaVersion,
    dontPublish
  )

lazy val coursier = project
  .in(root)
  .disablePlugins(ScriptedPlugin)
  .aggregate(
    coreJvm,
    coreJs,
    `fetch-js`,
    testsJvm,
    testsJs,
    `proxy-tests`,
    paths,
    cache,
    bootstrap,
    extra,
    cli,
    `sbt-shared`,
    `sbt-coursier`,
    `sbt-pgp-coursier`,
    `sbt-shading`,
    web,
    readme,
    okhttp
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-root"
  )


lazy val addBootstrapJarAsResource = {

  import java.nio.file.Files

  packageBin.in(Compile) := {
    val bootstrapJar = packageBin.in(bootstrap).in(Compile).value
    val source = packageBin.in(Compile).value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath)
    ))

    dest
  }
}

lazy val addBootstrapInProguardedJar = {

  import java.nio.charset.StandardCharsets
  import java.nio.file.Files

  proguard.in(Proguard) := {
    val bootstrapJar = packageBin.in(bootstrap).in(Compile).value
    val source = proguardedJar.value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")
    val dest0 = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap-and-prelude.jar")

    // TODO Get from cli original JAR
    val manifest =
      s"""Manifest-Version: 1.0
         |Implementation-Title: ${name.value}
         |Implementation-Version: ${version.value}
         |Specification-Vendor: ${organization.value}
         |Specification-Title: ${name.value}
         |Implementation-Vendor-Id: ${organization.value}
         |Specification-Version: ${version.value}
         |Implementation-URL: ${homepage.value.getOrElse("")}
         |Implementation-Vendor: ${organization.value}
         |Main-Class: ${mainClass.in(Compile).value.getOrElse(sys.error("Main class not found"))}
         |""".stripMargin

    ZipUtil.addToZip(source, dest, Seq(
      "bootstrap.jar" -> Files.readAllBytes(bootstrapJar.toPath),
      "META-INF/MANIFEST.MF" -> manifest.getBytes(StandardCharsets.UTF_8)
    ))

    ZipUtil.addPrelude(dest, dest0)

    Seq(dest0)
  }
}

lazy val proguardedCli = Seq(
  proguardVersion.in(Proguard) := SharedVersions.proguard,
  proguardOptions.in(Proguard) ++= Seq(
    "-dontwarn",
    "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
    "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
    "-adaptresourcefilenames **.properties"
  ),
  javaOptions.in(Proguard, proguard) := Seq("-Xmx3172M"),
  artifactPath.in(Proguard) := proguardDirectory.in(Proguard).value / "coursier-standalone.jar",
  artifacts ++= {
    if (scalaBinaryVersion.value == "2.11")
      Seq(proguardedArtifact.value)
    else
      Nil
  },
  addBootstrapInProguardedJar,
  addProguardedJar
)

lazy val sharedTestResources = {
  unmanagedResourceDirectories.in(Test) += baseDirectory.in(LocalRootProject).value / "tests" / "shared" / "src" / "test" / "resources"
}

// Using directly the sources of directories, rather than depending on it.
// This is required to use it from the bootstrap module, whose jar is launched as is (so shouldn't require dependencies).
// This is done for the other use of it too, from the cache module, not to have to manage two ways of depending on it.
lazy val addDirectoriesSources = {
  unmanagedSourceDirectories.in(Compile) += baseDirectory.in(LocalRootProject).value / "directories" / "src" / "main" / "java"
}

lazy val addPathsSources = Seq(
  addDirectoriesSources,
  unmanagedSourceDirectories.in(Compile) ++= unmanagedSourceDirectories.in(Compile).in(paths).value
)
