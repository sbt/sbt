package sbt

import java.io.FileInputStream

import org.apache.maven.repository.internal.PomExtraDependencyAttributes
import org.specs2._

class MavenResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2""".stripMargin

  This is a specification to check the maven resolution

  Resolving a maven dependency should
     handle sbt plugins                                           $resolveSbtPlugins
     use ivy for conflict resolution                              $resolveMajorConflicts
     handle cross configuration deps                              $resolveCrossConfigurations
     publish with maven-metadata                                  $publishMavenMetadata
     resolve transitive maven dependencies                        $resolveTransitiveMavenDependency
     resolve intransitive maven dependencies                      $resolveIntransitiveMavenDependency
     handle transitive configuration shifts                       $resolveTransitiveConfigurationMavenDependency
     resolve source and doc                                       $resolveSourceAndJavadoc
     resolve nonstandard (jdk5) classifier                        $resolveNonstandardClassifier
     Resolve pom artifact dependencies                            $resolvePomArtifactAndDependencies
     Fail if JAR artifact is not found w/ POM                     $failIfMainArtifactMissing
     Fail if POM.xml is not found                                 $failIfPomMissing
     resolve publication date for -SNAPSHOT                       $resolveSnapshotPubDate

                                                                """ // */

  // TODO - test latest.integration and .+

  def akkaActor = ModuleID("com.typesafe.akka", "akka-actor_2.11", "2.3.8", Some("compile"))
  def akkaActorTestkit = ModuleID("com.typesafe.akka", "akka-testkit_2.11", "2.3.8", Some("test"))
  def testngJdk5 = ModuleID("org.testng", "testng", "5.7", Some("compile")).classifier("jdk15")
  def jmxri = ModuleID("com.sun.jmx", "jmxri", "1.2.1", Some("compile"))
  def scalaLibraryAll = ModuleID("org.scala-lang", "scala-library-all", "2.11.4", Some("compile"))
  def scalaCompiler = ModuleID("org.scala-lang", "scala-compiler", "2.8.1", Some("scala-tool->default(compile)"))
  def scalaContinuationPlugin = ModuleID("org.scala-lang.plugins", "continuations", "2.8.1", Some("plugin->default(compile)"))
  def sbtPlugin =
    ModuleID("com.github.mpeltonen", "sbt-idea", "1.6.0", Some("compile")).
      extra(PomExtraDependencyAttributes.SbtVersionKey -> "0.13", PomExtraDependencyAttributes.ScalaVersionKey -> "2.10").
      copy(crossVersion = CrossVersion.Disabled)
  def oldSbtPlugin =
    ModuleID("com.github.mpeltonen", "sbt-idea", "1.6.0", Some("compile")).
      extra(PomExtraDependencyAttributes.SbtVersionKey -> "0.12", PomExtraDependencyAttributes.ScalaVersionKey -> "2.9.2").
      copy(crossVersion = CrossVersion.Disabled)
  def majorConflictLib = ModuleID("com.joestelmach", "natty", "0.3", Some("compile"))
  // TODO - This snapshot and resolver should be something we own/control so it doesn't disappear on us.
  def testSnapshot = ModuleID("com.typesafe", "config", "0.4.9-SNAPSHOT", Some("compile"))
  val SnapshotResolver = MavenRepository("some-snapshots", "https://oss.sonatype.org/content/repositories/snapshots/")

  override def resolvers = Seq(DefaultMavenRepository, SnapshotResolver, Resolver.publishMavenLocal)
  import Configurations.{ Compile, Test, Runtime, CompilerPlugin, ScalaTool }
  override def configurations = Seq(Compile, Test, Runtime, CompilerPlugin, ScalaTool)

  import ShowLines._

  def defaultUpdateOptions = UpdateOptions().withResolverConverter(MavenResolverConverter.converter)

  def resolveMajorConflicts = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")),
      Seq(majorConflictLib), None, defaultUpdateOptions)
    val report = ivyUpdate(m) // must not(throwAn[IllegalStateException])
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == Compile.name
        m <- conf.modules
        if (m.module.name contains "stringtemplate")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    jars must haveSize(1)
  }

  def resolveCrossConfigurations = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")),
      Seq(scalaCompiler, scalaContinuationPlugin), None, defaultUpdateOptions)
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == ScalaTool.name
        m <- conf.modules
        if (m.module.name contains "scala-compiler")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    jars must haveSize(1)
  }

  def resolveSbtPlugins = {

    def sha(f: java.io.File): String = sbt.Hash.toHex(sbt.Hash(f))
    def findSbtIdeaJars(dep: ModuleID, name: String) = {
      val m = module(ModuleID("com.example", name, "0.1.0", Some("compile")), Seq(dep), None, defaultUpdateOptions)
      val report = ivyUpdate(m)
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if (m.module.name contains "sbt-idea")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield (f, sha(f))
    }

    val oldJars = findSbtIdeaJars(oldSbtPlugin, "old")
    System.err.println(s"${oldJars.mkString("\n")}")
    val newJars = findSbtIdeaJars(sbtPlugin, "new")
    System.err.println(s"${newJars.mkString("\n")}")
    (newJars must haveSize(1)) and (oldJars must haveSize(1)) and (oldJars.map(_._2) must not(containTheSameElementsAs(newJars.map(_._2))))
  }

  def resolveSnapshotPubDate = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(testSnapshot), Some("2.10.2"), defaultUpdateOptions.withLatestSnapshots(true))
    val report = ivyUpdate(m)
    val pubTime =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if m.module.revision endsWith "-SNAPSHOT"
        date <- m.publicationDate
      } yield date
    (pubTime must haveSize(1))
  }

  def resolvePomArtifactAndDependencies = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(scalaLibraryAll), Some("2.10.2"), defaultUpdateOptions)
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if (m.module.name == "scala-library") || (m.module.name contains "parser")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    jars must haveSize(2)
  }

  def failIfPomMissing = {
    // TODO - we need the jar to not exist too.
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(ModuleID("org.scala-sbt", "does-not-exist", "1.0", Some("compile"))), Some("2.10.2"), defaultUpdateOptions)
    ivyUpdate(m) must throwAn[Exception]
  }

  def failIfMainArtifactMissing = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(jmxri), Some("2.10.2"), defaultUpdateOptions)
    ivyUpdate(m) must throwAn[Exception]
  }

  def resolveNonstandardClassifier = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(testngJdk5), Some("2.10.2"), defaultUpdateOptions)
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if m.module.name == "testng"
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    (report.configurations must haveSize(configurations.size)) and
      (jars must haveSize(1))
    (jars.forall(_.exists) must beTrue)

  }

  def resolveTransitiveMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActor), Some("2.10.2"), defaultUpdateOptions)
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if m.module.name == "scala-library"
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    (report.configurations must haveSize(configurations.size)) and
      (jars must not(beEmpty)) and
      (jars.forall(_.exists) must beTrue)

  }

  def resolveIntransitiveMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActorTestkit.intransitive()), Some("2.10.2"), defaultUpdateOptions)
    val report = ivyUpdate(m)
    val transitiveJars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if (m.module.name contains "akka-actor") && !(m.module.name contains "testkit")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    val directJars =
      for {
        conf <- report.configurations
        if conf.configuration == "compile"
        m <- conf.modules
        if (m.module.name contains "akka-actor") && (m.module.name contains "testkit")
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    (report.configurations must haveSize(configurations.size)) and
      (transitiveJars must beEmpty) and (directJars.forall(_.exists) must beTrue)
  }

  def resolveTransitiveConfigurationMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActorTestkit), Some("2.10.2"), defaultUpdateOptions)
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        if conf.configuration == "test"
        m <- conf.modules
        if m.module.name contains "akka-actor"
        (a, f) <- m.artifacts
        if a.extension == "jar"
      } yield f
    (report.configurations must haveSize(configurations.size)) and
      (jars must not(beEmpty)) and
      (jars.forall(_.exists) must beTrue)

  }

  def resolveSourceAndJavadoc = {
    val m = module(
      ModuleID("com.example", "foo", "0.1.0", Some("sources")),
      Seq(akkaActor.artifacts(Artifact(akkaActor.name, "javadoc"), Artifact(akkaActor.name, "sources"))),
      Some("2.10.2"),
      defaultUpdateOptions
    )
    val report = ivyUpdate(m)
    val jars =
      for {
        conf <- report.configurations
        //  We actually injected javadoc/sources into the compile scope, due to how we did the request.
        //  SO, we report that here.
        if conf.configuration == "compile"
        m <- conf.modules
        (a, f) <- m.artifacts
        if (f.getName contains "sources") || (f.getName contains "javadoc")
      } yield f
    (report.configurations must haveSize(configurations.size)) and
      (jars must haveSize(2))
  }

  def publishMavenMetadata = {
    val m = module(
      ModuleID("com.example", "test-it", "1.0-SNAPSHOT", Some("compile")),
      Seq(),
      None,
      defaultUpdateOptions.withLatestSnapshots(true)
    )
    sbt.IO.withTemporaryDirectory { dir =>
      val pomFile = new java.io.File(dir, "pom.xml")
      sbt.IO.write(pomFile,
        """
          |<project>
          |   <groupId>com.example</groupId>
          |   <name>test-it</name>
          |   <version>1.0-SNAPSHOT</version>
          |</project>
        """.stripMargin)
      val jarFile = new java.io.File(dir, "test-it-1.0-SNAPSHOT.jar")
      sbt.IO.touch(jarFile)
      System.err.println(s"DEBUGME - Publishing $m to ${Resolver.publishMavenLocal}")
      ivyPublish(m, mkPublishConfiguration(
        Resolver.publishMavenLocal,
        Map(
          Artifact("test-it-1.0-SNAPSHOT.jar") -> pomFile,
          Artifact("test-it-1.0-SNAPSHOT.pom", "pom", "pom") -> jarFile
        )))
    }
    val baseLocalMavenDir: java.io.File = Resolver.publishMavenLocal.rootFile
    val allFiles: Seq[java.io.File] = sbt.PathFinder(new java.io.File(baseLocalMavenDir, "com/example/test-it")).***.get
    val metadataFiles = allFiles.filter(_.getName contains "maven-metadata-local")
    // TODO - maybe we check INSIDE the metadata, or make sure we can get a publication date on resolve...
    // We end up with 4 files, two mavne-metadata files, and 2 maven-metadata-local files.
    metadataFiles must haveSize(2)
  }

}

