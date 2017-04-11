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
    extraRepo: Option[Repository] = None,
    profiles: Option[Set[String]] = None
  ) = {
    val repositories0 = extraRepo.toSeq ++ repositories

    val fetch = Platform.fetch(repositories0)

    Resolution(
      deps,
      filter = filter,
      userActivations = profiles.map(_.iterator.map(_ -> true).toMap)
    )
      .process
      .run(fetch)
      .runF
  }

  def resolutionCheck(
    module: Module,
    version: String,
    extraRepo: Option[Repository] = None,
    configuration: String = "",
    profiles: Option[Set[String]] = None
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
      val res = await(resolve(Set(dep), extraRepo = extraRepo, profiles = profiles))

      val result = res
        .minDependencies
        .toVector
        .map { dep =>
          val projOpt = res.projectCache
            .get(dep.moduleVersion)
            .map { case (_, proj) => proj }
          val dep0 = dep.copy(
            version = projOpt.fold(dep.version)(_.actualVersion)
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
        println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

      assert(result == expected)
    }

  def withArtifact[T](
    module: Module,
    version: String,
    artifactType: String,
    attributes: Attributes = Attributes(),
    extraRepo: Option[Repository] = None
  )(
    f: Artifact => T
  ): Future[T] =
    withArtifacts(module, version, artifactType, attributes, extraRepo) {
      case Seq(artifact) =>
        f(artifact)
      case other =>
        throw new Exception(
          s"Unexpected artifact list size: ${other.size}\n" +
            "Artifacts:\n" + other.map("  " + _).mkString("\n")
        )
    }

  def withArtifacts[T](
    module: Module,
    version: String,
    artifactType: String,
    attributes: Attributes = Attributes(),
    extraRepo: Option[Repository] = None,
    classifierOpt: Option[String] = None,
    transitive: Boolean = false
  )(
    f: Seq[Artifact] => T
  ): Future[T] = {
    val dep = Dependency(module, version, transitive = transitive, attributes = attributes)
    withArtifacts(dep, artifactType, extraRepo, classifierOpt)(f)
  }

  def withArtifacts[T](
    dep: Dependency,
    artifactType: String,
    extraRepo: Option[Repository],
    classifierOpt: Option[String]
  )(
    f: Seq[Artifact] => T
  ): Future[T] = 
    withArtifacts(Set(dep), artifactType, extraRepo, classifierOpt)(f)

  def withArtifacts[T](
    deps: Set[Dependency],
    artifactType: String,
    extraRepo: Option[Repository],
    classifierOpt: Option[String]
  )(
    f: Seq[Artifact] => T
  ): Future[T] = async {
    val res = await(resolve(deps, extraRepo = extraRepo))

    assert(res.errors.isEmpty)
    assert(res.conflicts.isEmpty)
    assert(res.isDone)

    val artifacts = classifierOpt.fold(res.dependencyArtifacts)(c => res.dependencyClassifiersArtifacts(Seq(c))).map(_._2).filter { a =>
      a.`type` == artifactType
    }

    f(artifacts)
  }

  def ensureHasArtifactWithExtension(
    module: Module,
    version: String,
    artifactType: String,
    extension: String,
    attributes: Attributes = Attributes(),
    extraRepo: Option[Repository] = None
  ): Future[Unit] =
    withArtifact(module, version, artifactType, attributes = attributes, extraRepo = extraRepo) { artifact =>
      assert(artifact.url.endsWith("." + extension))
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
            Dependency(Module("ch.qos.logback", "logback-core"), "1.1.3").withCompileScope,
            Dependency(Module("org.slf4j", "slf4j-api"), "1.7.7").withCompileScope))

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
            Dependency(Module("org.ow2.asm", "asm-tree"), "5.0.2").withCompileScope,
            Dependency(Module("org.ow2.asm", "asm"), "5.0.2").withCompileScope))

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
        "1.3.1",
        profiles = Some(Set("hadoop-2.2"))
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
      val mod = Module("com.github.fommil", "java-logging")
      val version = "1.2-SNAPSHOT"
      val extraRepo = MavenRepository("https://oss.sonatype.org/content/repositories/public/")

      * - resolutionCheck(
        mod,
        version,
        configuration = "runtime",
        extraRepo = Some(extraRepo)
      )

      * - ensureHasArtifactWithExtension(
        mod,
        version,
        "jar",
        "jar",
        extraRepo = Some(extraRepo)
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

    'projectProperties - {
      resolutionCheck(
        Module("org.glassfish.jersey.core", "jersey-client"),
        "2.19"
      )
    }

    'parentDependencyManagementProperties - {
      resolutionCheck(
        Module("com.nativelibs4java", "jnaerator-runtime"),
        "0.12"
      )
    }

    'artifactIdProperties - {
      resolutionCheck(
        Module("cc.factorie", "factorie_2.11"),
        "1.2"
      )
    }

    'versionInterval - {
      // Warning: needs to be updated when new versions of org.webjars.bower:jquery and
      // org.webjars.bower:jquery-mousewheel are published :-|
      resolutionCheck(
        Module("org.webjars.bower", "malihu-custom-scrollbar-plugin"),
        "3.1.5"
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

    'versionFromDependency - {
      val mod = Module("org.apache.ws.commons", "XmlSchema")
      val version = "1.1"
      val expectedArtifactUrl = "https://repo1.maven.org/maven2/org/apache/ws/commons/XmlSchema/1.1/XmlSchema-1.1.jar"

      * - resolutionCheck(mod, version)

      * - withArtifact(mod, version, "jar") { artifact =>
        assert(artifact.url == expectedArtifactUrl)
      }
    }

    'fixedVersionDependency - {
      val mod = Module("io.grpc", "grpc-netty")
      val version = "0.14.1"

      resolutionCheck(mod, version)
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

    'optionalScope - {

      def intransitiveCompiler(config: String) =
        Dependency(
          Module("org.scala-lang", "scala-compiler"), "2.11.8",
          configuration = config,
          transitive = false
        )

      withArtifacts(
        Set(
          intransitiveCompiler("default"),
          intransitiveCompiler("optional")
        ),
        "jar",
        None,
        None
      ) {
        case Seq() =>
          throw new Exception("Expected one JAR")
        case Seq(jar) =>
          () // ok
        case other =>
          throw new Exception(s"Got too many JARs (${other.mkString})")
      }
    }

    'packaging - {
      'aar - {
        // random aar-based module found on Central
        ensureHasArtifactWithExtension(
          Module("com.yandex.android", "speechkit"),
          "2.5.0",
          "aar",
          "aar"
        )
      }

      'bundle - {
        // has packaging bundle - ensuring coursier gives its artifact the .jar extension
        * - ensureHasArtifactWithExtension(
          Module("com.google.guava", "guava"),
          "17.0",
          "bundle",
          "jar"
        )

        // even though packaging is bundle, depending on attribute type "jar" should still find
        // an artifact
        * - ensureHasArtifactWithExtension(
          Module("com.google.guava", "guava"),
          "17.0",
          "bundle",
          "jar",
          attributes = Attributes("jar")
        )
      }

      'mavenPlugin - {
        // has packaging maven-plugin - ensuring coursier gives its artifact the .jar extension
        ensureHasArtifactWithExtension(
          Module("org.bytedeco", "javacpp"),
          "1.1",
          "maven-plugin",
          "jar"
        )
      }
    }

    'artifacts - {
      'uniqueness - {
        async {
          val deps = Set(
            Dependency(
              Module("org.scala-lang", "scala-compiler"), "2.11.8"
            ),
            Dependency(
              Module("org.scala-js", "scalajs-compiler_2.11.8"), "0.6.8"
            )
          )

          val res = await(resolve(deps))

          assert(res.errors.isEmpty)
          assert(res.conflicts.isEmpty)
          assert(res.isDone)

          val artifacts = res.artifacts

          val map = artifacts.groupBy(a => a)

          val nonUnique = map.filter {
            case (_, l) => l.length > 1
          }

          if (nonUnique.nonEmpty)
            println(
              "Found non unique artifacts:\n" +
                nonUnique.keys.toVector.map("  " + _).mkString("\n")
            )

          assert(nonUnique.isEmpty)
        }
      }

      'testJarType - {
        // dependencies with type "test-jar" should be given the classifier "tests" by default

        async {
          val deps = Set(
            Dependency(
              Module("org.apache.hadoop", "hadoop-yarn-server-resourcemanager"),
              "2.7.1"
            )
          )

          val res = await(resolve(deps))

          assert(res.errors.isEmpty)
          assert(res.conflicts.isEmpty)
          assert(res.isDone)

          val dependencyArtifacts = res.dependencyArtifacts

          val zookeeperTestArtifacts = dependencyArtifacts.collect {
            case (dep, artifact)
              if dep.module == Module("org.apache.zookeeper", "zookeeper") &&
                 dep.attributes.`type` == "test-jar" =>
              artifact
          }

          assert(zookeeperTestArtifacts.length == 1)

          val zookeeperTestArtifact = zookeeperTestArtifacts.head

          assert(zookeeperTestArtifact.attributes.`type` == "test-jar")
          assert(zookeeperTestArtifact.attributes.classifier == "tests")
          zookeeperTestArtifact.url.endsWith("-tests.jar")
        }
      }
    }

    'ignoreUtf8Bom - {
      resolutionCheck(
        Module("dk.brics.automaton", "automaton"),
        "1.11-8"
      )
    }

    'ignoreWhitespaces - {
      resolutionCheck(
        Module("org.jboss.resteasy", "resteasy-jaxrs"),
        "3.0.9.Final"
      )
    }

    'nd4jNative - {
      // In particular:
      // - uses OS-based activation,
      // - requires converting a "x86-64" to "x86_64" in it, and
      // - uses "project.packaging" property
      resolutionCheck(
        Module("org.nd4j", "nd4j-native"),
        "0.5.0"
      )
    }

    'scalaCompilerJLine - {

      // optional should bring jline

      * - resolutionCheck(
        Module("org.scala-lang", "scala-compiler"),
        "2.11.8"
      )

      * - resolutionCheck(
        Module("org.scala-lang", "scala-compiler"),
        "2.11.8",
        configuration = "optional"
      )
    }

    'deepLearning4j - {
      resolutionCheck(
        Module("org.deeplearning4j", "deeplearning4j-core"),
        "0.8.0"
      )
    }

    'tarGzZipArtifacts - {
      val mod = Module("org.apache.maven", "apache-maven")
      val version = "3.3.9"

      * - resolutionCheck(mod, version)

      val expectedTarGzArtifactUrls = Set(
        "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.tar.gz",
        "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3-bin.tar.gz"
      )

      val expectedZipArtifactUrls = Set(
        "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.zip",
        "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3-bin.zip"
      )

      * - withArtifacts(mod, version, "tar.gz", classifierOpt = Some("bin"), transitive = true) { artifacts =>
        assert(artifacts.length == 2)
        val urls = artifacts.map(_.url).toSet
        assert(urls == expectedTarGzArtifactUrls)
      }

      * - withArtifacts(mod, version, "zip", classifierOpt = Some("bin"), transitive = true) { artifacts =>
        assert(artifacts.length == 2)
        val urls = artifacts.map(_.url).toSet
        assert(urls == expectedZipArtifactUrls)
      }
    }
  }

}
