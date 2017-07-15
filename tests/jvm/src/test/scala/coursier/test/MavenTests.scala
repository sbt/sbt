package coursier.test

import coursier.{ Attributes, Dependency, Module }
import coursier.maven.MavenRepository

import utest._

object MavenTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  val tests = TestSuite {
    'testSnapshotNoVersioning - {

      val dep = Dependency(
        Module("com.abc", "test-snapshot-special"),
        "0.1.0-SNAPSHOT",
        transitive = false,
        attributes = Attributes()
      )

      val repoBase = getClass.getResource("/test-repo/http/abc.com").toString.stripSuffix("/") + "/"
      val repo = MavenRepository(repoBase)

      val mainJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82.jar"
      val sourcesJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82-sources.jar"

      * - CentralTests.withArtifacts(
        dep = dep,
        artifactType = "jar",
        extraRepos = Seq(repo),
        classifierOpt = None,
        optional = true
      ) {
        case Seq(artifact) =>
          assert(artifact.url == mainJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }

      * - CentralTests.withArtifacts(
        dep = dep,
        artifactType = "src",
        extraRepos = Seq(repo),
        classifierOpt = Some("sources"),
        optional = true
      ) {
        case Seq(artifact) =>
          assert(artifact.url == sourcesJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }
    }
  }
}
