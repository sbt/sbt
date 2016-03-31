package coursier
package test

import utest._
import scala.async.Async.{ async, await }

import coursier.Platform.fetch
import coursier.test.compatibility._

import scala.concurrent.Future

object CentralTests extends TestSuite {

  val repositories = Seq[Repository](
    MavenRepository("https://repo1.maven.org/maven2/")
  )

  def resolve(
    deps: Set[Dependency],
    filter: Option[Dependency => Boolean] = None,
    extraRepo: Option[Repository] = None
  ) = {
    val repositories0 = extraRepo.toSeq ++ repositories

    Resolution(deps, filter = filter)
      .process
      .run(repositories0)
      .runF
  }

  def resolutionCheck(
    module: Module,
    version: String,
    extraRepo: Option[Repository] = None,
    configuration: String = ""
  ) =
    async {
      val attrPathPart =
        if (module.attributes.isEmpty)
          ""
        else
          "/" + module.attributes.toVector.sorted.map {
            case (k, v) => k + "_" + v
          }.mkString("_")

      val expected =
        await(
          textResource(
            Seq(
              "resolutions",
              module.organization,
              module.name,
              attrPathPart,
              version + (
                if (configuration.isEmpty)
                  ""
                else
                  "_" + configuration.replace('(', '_').replace(')', '_')
              )
            ).filter(_.nonEmpty).mkString("/")
          )
        ).split('\n').toSeq

      val dep = Dependency(module, version, configuration = configuration)
      val res = await(resolve(Set(dep), extraRepo = extraRepo))

      val result = res
        .minDependencies
        .toVector
        .map { dep =>
          val projOpt = res.projectCache
            .get(dep.moduleVersion)
            .map { case (_, proj) => proj }
          val dep0 = dep.copy(
            version = projOpt.fold(dep.version)(_.version)
          )
          (dep0.module.organization, dep0.module.nameWithAttributes, dep0.version, dep0.configuration)
        }
        .sorted
        .distinct
        .map {
          case (org, name, ver, cfg) =>
            Seq(org, name, ver, cfg).mkString(":")
        }

      for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
        println(s"Line $idx:\n  expected: $e\n  got:      $r")

      assert(result == expected)
    }

  def ensureArtifactHasExtension(module: Module, version: String, extension: String): Future[Unit] = async {
    val dep = Dependency(module, version, transitive = false)
    val res = await(resolve(Set(dep)))

    res.artifacts match {
      case Seq(artifact) =>
        assert(artifact.url.endsWith("." + extension))
      case other =>
        throw new Exception(
          s"Unexpected artifact list size: ${other.size}\n" +
            "Artifacts:\n" + other.map("  " + _).mkString("\n")
        )
    }
  }

  val tests = TestSuite {

    'logback - {
      async {
        val dep = Dependency(Module("ch.qos.logback", "logback-classic"), "1.1.3")
        val res = await(resolve(Set(dep))).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module("ch.qos.logback", "logback-core"), "1.1.3").withCompileScope.withJarAttributeType,
            Dependency(Module("org.slf4j", "slf4j-api"), "1.7.7").withCompileScope.withJarAttributeType))

        assert(res == expected)
      }
    }

    'asm - {
      async {
        val dep = Dependency(Module("org.ow2.asm", "asm-commons"), "5.0.2")
        val res = await(resolve(Set(dep))).clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module("org.ow2.asm", "asm-tree"), "5.0.2").withCompileScope.withJarAttributeType,
            Dependency(Module("org.ow2.asm", "asm"), "5.0.2").withCompileScope.withJarAttributeType))

        assert(res == expected)
      }
    }

    'jodaVersionInterval - {
      async {
        val dep = Dependency(Module("joda-time", "joda-time"), "[2.2,2.8]")
        val res0 = await(resolve(Set(dep)))
        val res = res0.clearCaches

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope))

        assert(res == expected)
        assert(res0.projectCache.contains(dep.moduleVersion))

        val proj = res0.projectCache(dep.moduleVersion)._2
        assert(proj.version == "2.8")
      }
    }

    'spark - {
      resolutionCheck(
        Module("org.apache.spark", "spark-core_2.11"),
        "1.3.1"
      )
    }

    'argonautShapeless - {
      resolutionCheck(
        Module("com.github.alexarchambault", "argonaut-shapeless_6.1_2.11"),
        "0.2.0"
      )
    }

    'snapshotMetadata - {
      // Let's hope this one won't change too much
      resolutionCheck(
        Module("com.github.fommil", "java-logging"),
        "1.2-SNAPSHOT",
        configuration = "runtime",
        extraRepo = Some(MavenRepository("https://oss.sonatype.org/content/repositories/public/"))
      )
    }

    'versionProperty - {
      // nasty one - in its POM, its version contains "${parent.project.version}"
      resolutionCheck(
        Module("org.bytedeco.javacpp-presets", "opencv"),
        "3.0.0-1.1"
      )
    }

    'parentProjectProperties - {
      resolutionCheck(
        Module("com.github.fommil.netlib", "all"),
        "1.1.2"
      )
    }

    'latestRevision - {
      * - resolutionCheck(
        Module("com.chuusai", "shapeless_2.11"),
        "[2.2.0,2.3-a1)"
      )

      * - resolutionCheck(
        Module("com.chuusai", "shapeless_2.11"),
        "2.2.+"
      )

      * - resolutionCheck(
        Module("com.googlecode.libphonenumber", "libphonenumber"),
        "[7.0,7.1)"
      )

      * - resolutionCheck(
        Module("com.googlecode.libphonenumber", "libphonenumber"),
        "7.0.+"
      )
    }

    'mavenScopes - {
      def check(config: String) = resolutionCheck(
        Module("com.android.tools", "sdklib"),
        "24.5.0",
        configuration = config
      )

      'compile - check("compile")
      'runtime - check("runtime")
    }

    'packaging - {
      * - {
        // random aar-based module found on Central
        ensureArtifactHasExtension(
          Module("com.yandex.android", "speechkit"),
          "2.5.0",
          "aar"
        )
      }

      * - {
        // has packaging bundle - ensuring coursier gives its artifact the .jar extension
        ensureArtifactHasExtension(
          Module("com.google.guava", "guava"),
          "17.0",
          "jar"
        )
      }
    }
  }

}
