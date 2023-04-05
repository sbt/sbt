import scala.util.matching.Regex

lazy val repo = file("test-repo")
lazy val resolver = Resolver.file("test-repo", repo)

lazy val sbtPlugin1 = project.in(file("sbt-plugin-1"))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.example",
    name := "sbt-plugin-1",
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.5.0"),
    publishTo := Some(resolver),
    checkPackagedArtifacts := checkPackagedArtifactsDef("sbt-plugin-1", true).value,
    checkPublish := checkPublishDef("sbt-plugin-1", true).value
  )

lazy val testMaven1 = project.in(file("test-maven-1"))
  .settings(
    addSbtPlugin("org.example" % "sbt-plugin-1" % "0.1.0-SNAPSHOT"),
    externalResolvers -= Resolver.defaultLocal,
    resolvers += {
      val base = (ThisBuild / baseDirectory).value
      MavenRepository("test-repo", s"file://$base/test-repo")
    },
    checkUpdate := checkUpdateDef(
      "sbt-plugin-1_2.12_1.0-0.1.0-SNAPSHOT.jar",
      "sbt-plugin-example-diamond_2.12_1.0-0.5.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.3.0.jar",
    ).value
  )

lazy val testLocal1 = project.in(file("test-local-1"))
  .settings(
    addSbtPlugin("org.example" % "sbt-plugin-1" % "0.1.0-SNAPSHOT"),
    checkUpdate := checkUpdateDef(
      "sbt-plugin-1.jar", // resolved from local repository
      "sbt-plugin-example-diamond_2.12_1.0-0.5.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.3.0.jar",
    ).value
  )

// test publish without legacy Maven artifacts
lazy val sbtPlugin2 = project.in(file("sbt-plugin-2"))
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "org.example",
    name := "sbt-plugin-2",
    addSbtPlugin("ch.epfl.scala" % "sbt-plugin-example-diamond" % "0.5.0"),
    sbtPluginPublishLegacyMavenStyle := false,
    publishTo := Some(resolver),
    checkPackagedArtifacts := checkPackagedArtifactsDef("sbt-plugin-2", false).value,
    checkPublish := checkPublishDef("sbt-plugin-2", false).value,
  )

lazy val testMaven2 = project.in(file("test-maven-2"))
  .settings(
    addSbtPlugin("org.example" % "sbt-plugin-2" % "0.1.0-SNAPSHOT"),
    externalResolvers -= Resolver.defaultLocal,
    resolvers += {
      val base = (ThisBuild / baseDirectory).value
      MavenRepository("test-repo", s"file://$base/test-repo")
    },
    checkUpdate := checkUpdateDef(
      "sbt-plugin-2_2.12_1.0-0.1.0-SNAPSHOT.jar",
      "sbt-plugin-example-diamond_2.12_1.0-0.5.0.jar",
      "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-right_2.12_1.0-0.3.0.jar",
      "sbt-plugin-example-bottom_2.12_1.0-0.3.0.jar",
    ).value
  )

lazy val checkPackagedArtifacts = taskKey[Unit]("check the packaged artifacts")
lazy val checkPublish = taskKey[Unit]("check publish")
lazy val checkUpdate = taskKey[Unit]("check update")

def checkPackagedArtifactsDef(artifactName: String, withLegacy: Boolean): Def.Initialize[Task[Unit]] = Def.task {
  val packagedArtifacts = Keys.packagedArtifacts.value

  val legacyArtifacts = packagedArtifacts.keys.filter(a => a.name == artifactName)
  if (withLegacy) {
    assert(legacyArtifacts.size == 4)
    val legacyPom = legacyArtifacts.find(_.`type` == "pom")
    assert(legacyPom.isDefined)
    val legacyPomContent = IO.read(packagedArtifacts(legacyPom.get))
    assert(legacyPomContent.contains(s"<artifactId>$artifactName</artifactId>"))
    assert(legacyPomContent.contains(s"<artifactId>sbt-plugin-example-diamond</artifactId>"))
  } else {
    assert(legacyArtifacts.size == 0)
  }
  
  val artifactsWithCrossVersion =
    packagedArtifacts.keys.filter(a => a.name == s"${artifactName}_2.12_1.0")
  assert(artifactsWithCrossVersion.size == 4)
  val pomWithCrossVersion = artifactsWithCrossVersion.find(_.`type` == "pom")
  assert(pomWithCrossVersion.isDefined)
  val pomContent = IO.read(packagedArtifacts(pomWithCrossVersion.get))
  assert(pomContent.contains(s"<artifactId>${artifactName}_2.12_1.0</artifactId>"))
  assert(pomContent.contains(s"<artifactId>sbt-plugin-example-diamond_2.12_1.0</artifactId>"))
}

def checkPublishDef(artifactName: String, withLegacy: Boolean): Def.Initialize[Task[Unit]] = Def.task {
  val _ = publish.value
  val org = organization.value
  val files = IO.listFiles(repo / org.replace('.', '/') / s"${artifactName}_2.12_1.0" / "0.1.0-SNAPSHOT")
  val logger = streams.value.log
  
  assert(files.nonEmpty)
  
  val legacyRegex =
    s"$artifactName-${Regex.quote("0.1.0-SNAPSHOT")}(-javadoc|-sources)?(\\.jar|\\.pom)".r
  val legacyArtifacts = files.filter(f => legacyRegex.unapplySeq(f.name).isDefined)
  if (withLegacy) {
    val legacyJars = legacyArtifacts.map(_.name).filter(_.endsWith(".jar"))
    assert(legacyJars.size == 3, legacyJars.mkString(", ")) // bin, sources and javadoc
    val legacyPom = legacyArtifacts.find(_.name.endsWith(".pom"))
    assert(legacyPom.isDefined, "missing legacy pom")
    val legacyPomContent = IO.read(legacyPom.get)
    assert(legacyPomContent.contains(s"<artifactId>$artifactName</artifactId>"))
    assert(legacyPomContent.contains(s"<artifactId>sbt-plugin-example-diamond</artifactId>"))
  } else {
    assert(legacyArtifacts.size == 0)
  }
  
  val withCrossVersionRegex =
    s"$artifactName${Regex.quote("_2.12_1.0")}-${Regex.quote("0.1.0-SNAPSHOT")}(-javadoc|-sources)?(\\.jar|\\.pom)".r
  val artifactWithCrossVersion = files.filter(f => withCrossVersionRegex.unapplySeq(f.name).isDefined)
  val jarsWithCrossVersion = artifactWithCrossVersion.map(_.name).filter(_.endsWith(".jar"))
  assert(jarsWithCrossVersion.size == 3, jarsWithCrossVersion.mkString(", ")) // bin, sources and javadoc
  val pomWithCrossVersion = artifactWithCrossVersion.find(_.name.endsWith(".pom"))
  assert(pomWithCrossVersion.isDefined, "missing pom with sbt cross-version _2.12_1.0")
  val pomContent = IO.read(pomWithCrossVersion.get)
  assert(pomContent.contains(s"<artifactId>${artifactName}_2.12_1.0</artifactId>"))
  assert(pomContent.contains(s"<artifactId>sbt-plugin-example-diamond_2.12_1.0</artifactId>"))
}

def checkUpdateDef(expected: String*): Def.Initialize[Task[Unit]] = Def.task {
  val report = update.value
  val obtainedFiles = report.configurations
    .find(_.configuration.name == Compile.name)
    .toSeq
    .flatMap(_.modules)
    .flatMap(_.artifacts)
    .map(_._2)
  val obtainedSet = obtainedFiles.map(_.getName).toSet
  val expectedSet = expected.toSet + "scala-library.jar"
  assert(obtainedSet == expectedSet, obtainedSet)
}
