package coursier.test

import coursier.ivy.PropertiesPattern
import coursier.ivy.PropertiesPattern.ChunkOrProperty
import coursier.ivy.PropertiesPattern.ChunkOrProperty._

import utest._

object IvyPatternParserTests extends TestSuite {

  val tests = Tests {

    'plugin - {
      val strPattern = "[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/resolved.xml.[ext]"
      val expectedChunks = Seq[ChunkOrProperty](
        Var("organization"),
        "/", Var("module"),
        Opt("/scala_", Var("scalaVersion")),
        Opt("/sbt_", Var("sbtVersion")),
        "/", Var("revision"),
        "/resolved.xml.", Var("ext")
      )

      assert(PropertiesPattern.parse(strPattern).right.map(_.chunks) == Right(expectedChunks))
    }

    'activatorLaunchLocal - {
      val strPattern =
        "file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}" +
          "/[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
          "[revision]/[type]s/[artifact](-[classifier]).[ext]"
      val expectedChunks = Seq[ChunkOrProperty](
        "file://",
        Prop("activator.local.repository", Some(Seq(
          Prop("activator.home", Some(Seq(
            Prop("user.home", None),
            "/.activator"
          ))),
          "/repository"
        ))), "/",
        Var("organization"), "/",
        Var("module"), "/",
        Opt("scala_", Var("scalaVersion"), "/"),
        Opt("sbt_", Var("sbtVersion"), "/"),
        Var("revision"), "/",
        Var("type"), "s/",
        Var("artifact"), Opt("-", Var("classifier")), ".", Var("ext")
      )

      val pattern0 = PropertiesPattern.parse(strPattern)
      assert(pattern0.right.map(_.chunks) == Right(expectedChunks))

      val pattern = pattern0.right.get

      * - {
        val varPattern = pattern.substituteProperties(Map(
          "activator.local.repository" -> "xyz"
        )).right.map(_.string)

        val expectedVarPattern =
          "file://xyz" +
            "/[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
            "[revision]/[type]s/[artifact](-[classifier]).[ext]"

        assert(varPattern == Right(expectedVarPattern))
      }

      * - {
        val varPattern = pattern.substituteProperties(Map(
          "activator.local.repository" -> "xyz",
          "activator.home" -> "aaaa"
        )).right.map(_.string)

        val expectedVarPattern =
          "file://xyz" +
            "/[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
            "[revision]/[type]s/[artifact](-[classifier]).[ext]"

        assert(varPattern == Right(expectedVarPattern))
      }

      * - {
        val varPattern = pattern.substituteProperties(Map(
          "activator.home" -> "aaaa"
        )).right.map(_.string)

        val expectedVarPattern =
          "file://aaaa/repository" +
            "/[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
            "[revision]/[type]s/[artifact](-[classifier]).[ext]"

        assert(varPattern == Right(expectedVarPattern))
      }

      * - {
        val varPattern0 = pattern.substituteProperties(Map(
          "user.home" -> "homez"
        ))

        val expectedVarPattern =
          "file://homez/.activator/repository" +
            "/[organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
            "[revision]/[type]s/[artifact](-[classifier]).[ext]"

        assert(varPattern0.right.map(_.string) == Right(expectedVarPattern))

        val varPattern = varPattern0.right.toOption.get

        * - {
          val res = varPattern.substituteVariables(Map(
            "organization" -> "org",
            "module" -> "mod",
            "revision" -> "1.1.x",
            "type" -> "jarr",
            "artifact" -> "art",
            "classifier" -> "docc",
            "ext" -> "jrr"
          )).right.map(_.string)
          val expectedRes = "file://homez/.activator/repository/org/mod/1.1.x/jarrs/art-docc.jrr"

          assert(res == Right(expectedRes))
        }
      }

      * - {
        val varPattern = pattern.substituteProperties(Map())
        assert(varPattern.isLeft)
      }
    }

  }

}
