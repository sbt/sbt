package coursier.test

import coursier.Module
import coursier.ivy.IvyRepository

import utest._

object IvyTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  val sbtRepo = IvyRepository.parse(
    "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/" +
      "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
      "[revision]/[type]s/[artifact](-[classifier]).[ext]",
    dropInfoAttributes = true
  ).getOrElse(
    throw new Exception("Cannot happen")
  )

  val tests = TestSuite {
    'dropInfoAttributes - {
      CentralTests.resolutionCheck(
        module = Module(
          "org.scala-js", "sbt-scalajs", Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
        ),
        version = "0.6.6",
        extraRepo = Some(sbtRepo),
        configuration = "default(compile)"
      )
    }

    'versionIntervals - {
      // will likely break if new 0.6.x versions are published :-)

      val mod = Module(
        "com.github.ddispaltro", "sbt-reactjs", Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
      )
      val ver = "0.6.+"

      val expectedArtifactUrl = "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/com.github.ddispaltro/sbt-reactjs/scala_2.10/sbt_0.13/0.6.8/jars/sbt-reactjs.jar"

      * - CentralTests.resolutionCheck(
        module = mod,
        version = ver,
        extraRepo = Some(sbtRepo)
      )

      * - CentralTests.withArtifact(mod, ver, extraRepo = Some(sbtRepo)) { artifact =>
        assert(artifact.url == expectedArtifactUrl)
      }
    }
  }

}
