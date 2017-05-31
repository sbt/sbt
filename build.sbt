
import Aliases._
import Settings._
import Publish._

parallelExecution.in(Global) := false

lazy val core = crossProject
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
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(coreJs)
  .settings(
    shared,
    dontPublish,
    coursierPrefix
  )

lazy val tests = crossProject
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

lazy val cache = project
  .dependsOn(coreJvm)
  .settings(
    shared,
    Mima.previousArtifacts,
    coursierPrefix,
    libs += Deps.scalazConcurrent,
    Mima.cacheFilters
  )

lazy val bootstrap = project
  .settings(
    pureJava,
    dontPublish,
    // seems not to be automatically found with sbt 0.13.16-M1 :-/
    mainClass := Some("coursier.Bootstrap"),
    renameMainJar("bootstrap.jar")
  )

lazy val extra = project
  .dependsOn(coreJvm)
  .settings(
    shared,
    coursierPrefix
  )

lazy val cli = project
  .dependsOn(coreJvm, cache, extra)
  .settings(
    shared,
    dontPublishIn("2.10", "2.12"),
    generatePack,
    proguard,
    coursierPrefix,
    libs ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          Deps.caseApp,
          Deps.argonautShapeless
        )
      else
        Seq()
    },
    addBootstrapJarAsResource,
    proguardedCli
  )

lazy val web = project
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

lazy val doc = project
  .dependsOn(coreJvm, cache)
  .enablePlugins(TutPlugin)
  .settings(
    shared,
    dontPublish,
    tutSourceDirectory := baseDirectory.value,
    tutTargetDirectory := baseDirectory.in(LocalRootProject).value
  )

// Don't try to compile that if you're not in 2.10
lazy val `sbt-coursier` = project
  .dependsOn(coreJvm, cache, extra)
  .settings(plugin)

// Don't try to compile that if you're not in 2.10
lazy val `sbt-shading` = project
  .enablePlugins(ShadingPlugin)
  .dependsOn(`sbt-coursier`)
  .settings(
    plugin,
    shading,
    localM2Repository, // for a possibly locally published jarjar
    libs += Deps.jarjar.value % "shaded",
    // dependencies of jarjar-core - directly depending on these so that they don't get shaded
    libs ++= Deps.jarjarTransitiveDeps
  )

lazy val `sbt-launcher` = project
  .dependsOn(cache)
  .settings(
    shared,
    generatePack,
    dontPublishIn("2.10", "2.12"),
    libs ++= {
      if (scalaBinaryVersion.value == "2.11")
        Seq(
          Deps.caseApp,
          Deps.sbtLauncherInterface,
          Deps.typesafeConfig
        )
      else
        Nil
    }
  )

lazy val `http-server` = project
  .settings(
    shared,
    generatePack,
    name := "http-server",
    dontPublishIn("2.10", "2.11"),
    libs ++= {
      if (scalaBinaryVersion.value == "2.12")
        Seq(
          Deps.http4sBlazeServer,
          Deps.http4sDsl,
          Deps.slf4jNop,
          Deps.caseApp12
        )
      else
        Nil
    }
  )

lazy val okhttp = project
  .dependsOn(cache)
  .settings(
    shared,
    coursierPrefix,
    libs += Deps.okhttpUrlConnection
  )

lazy val echo = project
  .settings(pureJava)

lazy val jvm = project
  .dummy
  .aggregate(
    coreJvm,
    testsJvm,
    cache,
    bootstrap,
    extra,
    cli,
    `sbt-coursier`,
    `sbt-shading`,
    `sbt-launcher`,
    doc,
    `http-server`,
    okhttp,
    echo
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-jvm"
  )

lazy val js = project
  .dummy
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
  .aggregate(
    coreJvm,
    cache,
    extra,
    `sbt-coursier`,
    `sbt-shading`
  )
  .settings(
    shared,
    dontPublish
  )

lazy val coursier = project
  .in(root)
  .aggregate(
    coreJvm,
    coreJs,
    `fetch-js`,
    testsJvm,
    testsJs,
    cache,
    bootstrap,
    extra,
    cli,
    `sbt-coursier`,
    `sbt-shading`,
    `sbt-launcher`,
    web,
    doc,
    echo,
    `http-server`,
    okhttp
  )
  .settings(
    shared,
    dontPublish,
    moduleName := "coursier-root"
  )


lazy val addBootstrapJarAsResource = {
  resourceGenerators.in(Compile) += packageBin.in(bootstrap).in(Compile).map(Seq(_)).taskValue
}

lazy val addBootstrapInProguardedJar = {
  ProguardKeys.proguard.in(Proguard) := {
    val bootstrapJar = packageBin.in(bootstrap).in(Compile).value
    val source = proguardedJar.value

    val dest = source.getParentFile / (source.getName.stripSuffix(".jar") + "-with-bootstrap.jar")

    ZipUtil.addToZip(source, dest, Seq("bootstrap.jar" -> bootstrapJar))

    Seq(dest)
  }
}

lazy val proguardedCli = Seq(
  ProguardKeys.proguardVersion.in(Proguard) := SharedVersions.proguard,
  ProguardKeys.options.in(Proguard) ++= Seq(
    "-dontwarn",
    "-keep class coursier.cli.Coursier {\n  public static void main(java.lang.String[]);\n}",
    "-keep class coursier.cli.IsolatedClassLoader {\n  public java.lang.String[] getIsolationTargets();\n}",
    "-adaptresourcefilenames **.properties"
  ),
  javaOptions.in(Proguard, ProguardKeys.proguard) := Seq("-Xmx3172M"),
  artifactPath.in(Proguard) := ProguardKeys.proguardDirectory.in(Proguard).value / "coursier-standalone.jar",
  artifacts ++= {
    if (scalaBinaryVersion.value == "2.11")
      Seq(proguardedArtifact.value)
    else
      Nil
  },
  addBootstrapInProguardedJar,
  packagedArtifacts ++= {
    if (scalaBinaryVersion.value == "2.11")
      Map(proguardedArtifact.value -> proguardedJar.value)
    else
      Map()
  }
)

lazy val sharedTestResources = {
  unmanagedResourceDirectories.in(Test) += baseDirectory.in(LocalRootProject).value / "tests" / "shared" / "src" / "test" / "resources"
}
