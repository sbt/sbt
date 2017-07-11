package sbt.internal.librarymanagement

import org.scalatest.Inside
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import InternalDefaults._

class IvyRepoSpec extends BaseIvySpecification {

  val ourModuleID = ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile"))

  def makeModuleForDepWithSources = {
    // By default a module seems to only have [compile, test, runtime], yet deps automatically map to
    // default->compile(default) ... so I guess we have to explicitly use e.g. "compile"
    val dep = "com.test" % "module-with-srcs" % "0.1.00" % "compile"

    module(
      ourModuleID,
      Vector(dep),
      None //, UpdateOptions().withCachedResolution(true)
    )
  }

  "ivyUpdate from ivy repository" should "resolve only binary artifact from module which also contains a sources artifact under the same configuration." in {
    cleanIvyCache()

    val m = makeModuleForDepWithSources

    val report = ivyUpdate(m)

    import Inside._
    inside(report.configuration(ConfigRef("compile")).map(_.modules)) {
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

    // the "default" configuration used in `update`.
    val c = makeUpdateConfiguration(false, None)

    val scalaModuleInfo = m.moduleSettings.scalaModuleInfo
    val srcTypes = Vector("src")
    val docTypes = Vector("javadoc")
    // These will be the default classifiers that SBT should try, in case a dependency is Maven.
    // In this case though, they will be tried and should fail gracefully - only the
    val attemptedClassifiers = Vector("sources", "javadoc")

    // The dep that we want to get the "classifiers" (i.e. sources / docs) for.
    // We know it has only one source artifact in the "compile" configuration.
    val dep = "com.test" % "module-with-srcs" % "0.1.00" % "compile"

    val clMod = {
      val externalModules = Vector(dep)
      // Note: need to extract ourModuleID so we can plug it in here, can't fish it back out of the IvySbt#Module (`m`)
      GetClassifiersModule(ourModuleID,
                           scalaModuleInfo,
                           externalModules,
                           Vector(Configurations.Compile),
                           attemptedClassifiers)
    }

    val artifactFilter = getArtifactTypeFilter(c.artifactFilter)
    val gcm = GetClassifiersConfiguration(clMod,
                                          Vector.empty,
                                          c.withArtifactFilter(artifactFilter.invert),
                                          srcTypes,
                                          docTypes)

    val report2 =
      lmEngine()
        .updateClassifiers(gcm, UnresolvedWarningConfiguration(), Vector(), log)
        .right
        .get

    import Inside._
    inside(report2.configuration(ConfigRef("compile")).map(_.modules)) {
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
