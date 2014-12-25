package sbt

import org.specs2._

class MavenResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2""".stripMargin

  This is a specification to check the maven resolution

  Resolving a maven dependency should
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
                                                                """

  // TODO - Check that if we have two repositories, where ONE has just a pom and ONE has pom + jar, that hte pom + jar is used.
  //        Note - This may be a fundamental issue with Aether vs. Ivy.   Ideally we don't want to make a network "existence"
  //               check EVERY time we grab something from the cache.
  //               Additionally, we should find a way to test NOT hititng the network for things like
  //               checking if a JAR is associated with pom-packaged artifact.
  // TODO - latest.integration
  // TODO - Check whether maven-metadata.xml is updated on deploy/install
  //
  /* Failing tests -
[error] 	dependency-management / cached-resolution-classifier
[error] 	dependency-management / cache-classifiers
[error] 	dependency-management / metadata-only-resolver
[error] 	dependency-management / mvn-local

  */
  def akkaActor = ModuleID("com.typesafe.akka", "akka-actor_2.11", "2.3.8", Some("compile"))
  def akkaActorTestkit = ModuleID("com.typesafe.akka", "akka-testkit_2.11", "2.3.8", Some("test"))
  def testngJdk5 = ModuleID("org.testng", "testng", "5.7", Some("compile")).classifier("jdk15")
  def jmxri = ModuleID("com.sun.jmx", "jmxri", "1.2.1", Some("compile"))
  def scalaLibraryAll = ModuleID("org.scala-lang", "scala-library-all", "2.11.4", Some("compile"))

  // TODO - This snapshot and resolver should be something we own/control so it doesn't disappear on us.
  def testSnapshot = ModuleID("com.typesafe", "config", "0.4.9-SNAPSHOT", Some("compile"))
  val SnapshotResolver = MavenRepository("some-snapshots", "https://oss.sonatype.org/content/repositories/snapshots/")

  override def resolvers = Seq(DefaultMavenRepository, SnapshotResolver, Resolver.publishMavenLocal)

  import ShowLines._

  def resolveSnapshotPubDate = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(testSnapshot), Some("2.10.2"), UpdateOptions().withLatestSnapshots(true))
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
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(scalaLibraryAll), Some("2.10.2"), UpdateOptions())
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
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(ModuleID("org.scala-sbt", "does-not-exist", "1.0", Some("compile"))), Some("2.10.2"), UpdateOptions())
    ivyUpdate(m) must throwAn[Exception]
  }

  def failIfMainArtifactMissing = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(jmxri), Some("2.10.2"), UpdateOptions())
    ivyUpdate(m) must throwAn[Exception]
  }

  def resolveNonstandardClassifier = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(testngJdk5), Some("2.10.2"), UpdateOptions())
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
    (report.configurations must haveSize(3)) and
      (jars must haveSize(1))
    (jars.forall(_.exists) must beTrue)

  }

  def resolveTransitiveMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActor), Some("2.10.2"), UpdateOptions())
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
    (report.configurations must haveSize(3)) and
      (jars must not(beEmpty)) and
      (jars.forall(_.exists) must beTrue)

  }

  def resolveIntransitiveMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActorTestkit.intransitive()), Some("2.10.2"), UpdateOptions())
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
    (report.configurations must haveSize(3)) and
      (transitiveJars must beEmpty) and (directJars.forall(_.exists) must beTrue)
  }

  def resolveTransitiveConfigurationMavenDependency = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(akkaActorTestkit), Some("2.10.2"), UpdateOptions())
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
    (report.configurations.size must_== 3) and
      (jars must not(beEmpty)) and
      (jars.forall(_.exists) must beTrue)

  }

  def resolveSourceAndJavadoc = {
    val m = module(
      ModuleID("com.example", "foo", "0.1.0", Some("sources")),
      Seq(akkaActor.artifacts(Artifact(akkaActor.name, "javadoc"), Artifact(akkaActor.name, "sources"))),
      Some("2.10.2"),
      UpdateOptions()
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
    (report.configurations must haveSize(3)) and
      (jars must haveSize(2))
  }

  def publishMavenMetadata = {
    val m = module(
      ModuleID("com.example", "test-it", "1.0-SNAPSHOT", Some("compile")),
      Seq(),
      None,
      UpdateOptions().withLatestSnapshots(true)
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
    val baseLocalMavenDir = new java.io.File(new java.net.URI(Resolver.publishMavenLocal.root))
    val allFiles: Seq[java.io.File] = sbt.PathFinder(new java.io.File(baseLocalMavenDir, "com/example/test-it")).***.get
    val metadataFiles = allFiles.filter(_.getName contains "maven-metadata")
    // TODO - maybe we check INSIDE the metadata, or make sure we can get a publication date on resolve...
    metadataFiles must haveSize(2)
  }

}

