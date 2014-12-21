package sbt

import org.specs2._

class MavenResolutionSpec extends BaseIvySpecification {
  def is = args(sequential = true) ^ s2""".stripMargin

  This is a specification to check the maven resolution

  Resolving a maven dependency should
     resolve transitive maven dependencies                        $resolveTransitiveMavenDependency
     resolve intransitive maven dependencies                      $resolveIntransitiveMavenDependency
     handle transitive configuration shifts                       $resolveTransitiveConfigurationMavenDependency
     resolve source and doc                                       $resolveSourceAndJavadoc
     resolve nonstandard (jdk5) classifier                        $resolveNonstandardClassifier
     Resolve pom artifact dependencies                            $resolvePomArtifactAndDependencies
     Fail if JAR artifact is not found w/ POM                     $failIfMainArtifactMissing
                                                                """

  // TODO - latest.integration
  // TODO - Check whether maven-metadata.xml is updated on deploy/install
  // TODO - Read lastModified/publicationDate from metadata.xml
  //
  /* Failing tests -
[error] 	dependency-management / cached-resolution-force
[error] 	dependency-management / exclude-transitive
[error] 	dependency-management / latest-local-plugin
[error] 	dependency-management / inline-dependencies-a
[error] 	dependency-management / artifact
[error] 	dependency-management / cached-resolution-classifier
[error] 	dependency-management / publish-local
[error] 	dependency-management / cache-classifiers
[error] 	dependency-management / t468
[error] 	dependency-management / ext-pom-classifier
[error] 	dependency-management / pom-classpaths
[error] 	dependency-management / metadata-only-resolver
[error] 	dependency-management / pom-advanced
[error] 	dependency-management / extra
[error] 	dependency-management / cache-local
[error] 	dependency-management / force
[error] 	dependency-management / mvn-local
  */
  def akkaActor = ModuleID("com.typesafe.akka", "akka-actor_2.11", "2.3.8", Some("compile"))
  def akkaActorTestkit = ModuleID("com.typesafe.akka", "akka-testkit_2.11", "2.3.8", Some("test"))
  def testngJdk5 = ModuleID("org.testng", "testng", "5.7", Some("compile")).classifier("jdk15")
  def jmxri = ModuleID("com.sun.jmx", "jmxri", "1.2.1", Some("compile"))
  def scalaLibraryAll = ModuleID("org.scala-lang", "scala-library-all", "2.11.4", Some("compile"))

  import ShowLines._

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

  def failIfMainArtifactMissing = {
    val m = module(ModuleID("com.example", "foo", "0.1.0", Some("compile")), Seq(jmxri), Some("2.10.2"), UpdateOptions())
    def doTest = {
      val result = ivyUpdate(m)
      for {
        c <- result.configurations
        m <- c.modules
      } {
        System.err.println(s"DEBUGME: Resolution report for ${m.module} in ${c.configuration}")
        for (p <- m.problem)
          System.err.println(s"  - issue: ${p}")
        for (a <- m.missingArtifacts) {
          System.err.println(s"  - missing: $a")
        }
        for ((a, f) <- m.artifacts) {
          System.err.println(s"  - downloaded: $a at $f")
        }
      }
    }
    doTest must throwAn[Exception]
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

}

