package sbt.internal.librarymanagement

import org.scalatest.Inside
import sbt.internal.librarymanagement.impl.DependencyBuilders
import sbt.librarymanagement._

class IvyRepoSpec extends BaseIvySpecification with DependencyBuilders {

  val ourModuleID = ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile"))

  def makeModuleForDepWithSources = {
    // By default a module seems to only have [compile, test, runtime], yet deps automatically map to
    // default->compile(default) ... so I guess we have to explicitly use e.g. "compile"
    val dep = "com.test" % "module-with-srcs" % "0.1.00" % "compile"

    module(
      ourModuleID,
      Vector(dep), None //, UpdateOptions().withCachedResolution(true)
    )
  }

  "ivyUpdate from ivy repository" should "resolve only binary artifact from module which also contains a sources artifact under the same configuration." in {
    cleanIvyCache()

    val m = makeModuleForDepWithSources

    val report = ivyUpdate(m)

    import Inside._
    inside(report.configuration("compile").map(_.modules)) {
      case Some(Seq(mr)) =>
        inside(mr.artifacts) {
          case Seq((ar, _)) =>
            ar.`type` shouldBe "jar"
            ar.extension shouldBe "jar"
        }
    }
  }

  it should "resolve only sources artifact of an acceptable artifact type, \"src\", when calling updateClassifiers." in {
    cleanIvyCache()

    val m = makeModuleForDepWithSources

    // the "default" configuration used in updateEither.
    val c = makeUpdateConfiguration

    val ivyScala = m.moduleSettings.ivyScala
    val srcTypes = Set("src")
    val docTypes = Set("javadoc")
    // These will be the default classifiers that SBT should try, in case a dependency is Maven.
    // In this case though, they will be tried and should fail gracefully - only the
    val attemptedClassifiers = Vector("sources", "javadoc")

    // The dep that we want to get the "classifiers" (i.e. sources / docs) for.
    // We know it has only one source artifact in the "compile" configuration.
    val dep = "com.test" % "module-with-srcs" % "0.1.00" % "compile"

    val clMod = {
      import language.implicitConversions
      val externalModules = Vector(dep)
      // Note: need to extract ourModuleID so we can plug it in here, can't fish it back out of the IvySbt#Module (`m`)
      GetClassifiersModule(ourModuleID, externalModules, Vector(Configurations.Compile), attemptedClassifiers)
    }

    val gcm = GetClassifiersConfiguration(clMod, Map.empty, c.withArtifactFilter(c.artifactFilter.invert), ivyScala, srcTypes, docTypes)

    val report2 = IvyActions.updateClassifiers(m.owner, gcm, UnresolvedWarningConfiguration(), LogicalClock.unknown, None, Vector(), log)

    import Inside._
    inside(report2.configuration("compile").map(_.modules)) {
      case Some(Seq(mr)) =>
        inside(mr.artifacts) {
          case Seq((ar, _)) =>
            ar.name shouldBe "libmodule-source"
            ar.`type` shouldBe "src"
            ar.extension shouldBe "jar"
        }
    }
  }

  override lazy val resolvers: Vector[Resolver] = Vector(testIvy)

  lazy val testIvy = {
    val repoUrl = getClass.getResource("/test-ivy-repo")
    Resolver.url("Test Repo", repoUrl)(Resolver.ivyStylePatterns)
  }
}
