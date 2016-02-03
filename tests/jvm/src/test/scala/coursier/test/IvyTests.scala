package coursier.test

import coursier.Module
import coursier.ivy.IvyRepository

import utest._

object IvyTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in our XML wrappers

  val tests = TestSuite {
    'dropInfoAttributes - {
      CentralTests.resolutionCheck(
        module = Module(
          "org.scala-js", "sbt-scalajs", Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
        ),
        version = "0.6.6",
        extraRepo = Some(
          IvyRepository(
            "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/" +
              "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
              "[revision]/[type]s/[artifact](-[classifier]).[ext]",
            dropInfoAttributes = true
          )
        )
      )
    }
  }

}
