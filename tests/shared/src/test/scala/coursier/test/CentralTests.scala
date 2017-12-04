package coursier
package test

import utest._
import scala.async.Async.{ async, await }

import coursier.Platform.fetch
import coursier.test.compatibility._

import scala.concurrent.Future

object CentralTests extends CentralTests

abstract class CentralTests extends TestSuite {

  def centralBase = "https://repo1.maven.org/maven2"

  final def isActualCentral = centralBase == "https://repo1.maven.org/maven2"

  val repositories = Seq[Repository](
    MavenRepository(centralBase)
  )

  // different return type on JVM and JS...
  private def fetch(repositories: Seq[Repository]) =
    Fetch.from(repositories, compatibility.artifact)

  def resolve(
    deps: Set[Dependency],
    filter: Option[Dependency => Boolean] = None,
    extraRepos: Seq[Repository] = Nil,
    profiles: Option[Set[String]] = None
  ) = {
    val repositories0 = extraRepos ++ repositories

    val fetch0 = fetch(repositories0)

    Resolution(
      deps,
      filter = filter,
      userActivations = profiles.map(_.iterator.map(_ -> true).toMap)
    )
      .process
      .run(fetch0)
      .map { res =>

        assert(res.metadataErrors.isEmpty)
        assert(res.conflicts.isEmpty)
        assert(res.isDone)

        res
      }
      .runF
  }

  def resolutionCheck(
    module: Module,
    version: String,
    extraRepos: Seq[Repository] = Nil,
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

      val path = Seq(
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

      def tryRead = textResource(path)

      val dep = Dependency(module, version, configuration = configuration)
      val res = await(resolve(Set(dep), extraRepos = extraRepos, profiles = profiles))

      // making that lazy makes scalac crash in 2.10 with scalajs
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

      val expected =
        await(
          tryRead.recoverWith {
            case _: Exception =>
              tryCreate(path, result.mkString("\n"))
              tryRead
          }
        ).split('\n').toSeq

      for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
        println(s"Line ${idx + 1}:\n  expected: $e\n  got:      $r")

      assert(result == expected)
    }

  def withArtifact[T](
    module: Module,
    version: String,
    artifactType: String,
    attributes: Attributes = Attributes(),
    extraRepos: Seq[Repository] = Nil
  )(
    f: Artifact => T
  ): Future[T] =
    withArtifacts(module, version, artifactType, attributes, extraRepos) {
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
    extraRepos: Seq[Repository] = Nil,
    classifierOpt: Option[String] = None,
    transitive: Boolean = false,
    optional: Boolean = true
  )(
    f: Seq[Artifact] => T
  ): Future[T] = {
    val dep = Dependency(module, version, transitive = transitive, attributes = attributes)
    withArtifacts(dep, artifactType, extraRepos, classifierOpt, optional)(f)
  }

  def withArtifacts[T](
    dep: Dependency,
    artifactType: String,
    extraRepos: Seq[Repository],
    classifierOpt: Option[String],
    optional: Boolean
  )(
    f: Seq[Artifact] => T
  ): Future[T] = 
    withArtifacts(Set(dep), artifactType, extraRepos, classifierOpt, optional)(f)

  def withArtifacts[T](
    deps: Set[Dependency],
    artifactType: String,
    extraRepos: Seq[Repository],
    classifierOpt: Option[String],
    optional: Boolean
  )(
    f: Seq[Artifact] => T
  ): Future[T] = async {
    val res = await(resolve(deps, extraRepos = extraRepos))

    assert(res.metadataErrors.isEmpty)
    assert(res.conflicts.isEmpty)
    assert(res.isDone)

    val artifacts = classifierOpt
      .fold(res.dependencyArtifacts(withOptional = optional))(c => res.dependencyClassifiersArtifacts(Seq(c)))
      .map(_._2)
      .filter {
        if (artifactType == "*") _ => true
        else
          _.`type` == artifactType
      }

    f(artifacts)
  }

  def ensureHasArtifactWithExtension(
    module: Module,
    version: String,
    artifactType: String,
    extension: String,
    attributes: Attributes = Attributes(),
    extraRepos: Seq[Repository] = Nil
  ): Future[Unit] =
    withArtifact(module, version, artifactType, attributes = attributes, extraRepos = extraRepos) { artifact =>
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
      'simple - {
        val mod = Module("com.github.fommil", "java-logging")
        val version = "1.2-SNAPSHOT"
        val extraRepo = MavenRepository("https://oss.sonatype.org/content/repositories/public/")

        * - resolutionCheck(
          mod,
          version,
          configuration = "runtime",
          extraRepos = Seq(extraRepo)
        )

        * - ensureHasArtifactWithExtension(
          mod,
          version,
          "jar",
          "jar",
          extraRepos = Seq(extraRepo)
        )
      }

      * - {
        val mod = Module("org.jitsi", "jitsi-videobridge")
        val version = "1.0-SNAPSHOT"
        val extraRepos = Seq(
          MavenRepository("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases"),
          MavenRepository("https://github.com/jitsi/jitsi-maven-repository/raw/master/snapshots"),
          MavenRepository("https://jitpack.io")
        )

        * - resolutionCheck(
          mod,
          version,
          extraRepos = extraRepos
        )
      }
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

    'propertySubstitution - {
      resolutionCheck(
        Module("org.drools", "drools-compiler"),
        "7.0.0.Final"
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
      val expectedArtifactUrl = s"$centralBase/org/apache/ws/commons/XmlSchema/1.1/XmlSchema-1.1.jar"

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
        extraRepos = Nil,
        classifierOpt = None,
        optional = true
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
        val module = Module("com.yandex.android", "speechkit")
        val version = "2.5.0"
        val tpe = "aar"

        * - ensureHasArtifactWithExtension(
          module,
          version,
          tpe,
          tpe,
          attributes = Attributes(tpe)
        )

        * - {
          if (isActualCentral)
            ensureHasArtifactWithExtension(
              module,
              version,
              tpe,
              tpe
            )
        }
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

          assert(res.metadataErrors.isEmpty)
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

          assert(res.metadataErrors.isEmpty)
          assert(res.conflicts.isEmpty)
          assert(res.isDone)

          val dependencyArtifacts = res.dependencyArtifacts(withOptional = true)

          val zookeeperTestArtifacts = dependencyArtifacts.collect {
            case (dep, artifact)
              if dep.module == Module("org.apache.zookeeper", "zookeeper") &&
                 dep.attributes.`type` == "test-jar" =>
              artifact
          }

          assert(zookeeperTestArtifacts.length == 1)

          val zookeeperTestArtifact = zookeeperTestArtifacts.head

          assert(!isActualCentral || !zookeeperTestArtifact.isOptional)
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

      val mainTarGzUrl = s"$centralBase/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.tar.gz"
      val expectedTarGzArtifactUrls = Set(
        mainTarGzUrl,
        s"$centralBase/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3-bin.tar.gz"
      )

      val mainZipUrl = s"$centralBase/org/apache/maven/apache-maven/3.3.9/apache-maven-3.3.9-bin.zip"
      val expectedZipArtifactUrls = Set(
        mainZipUrl,
        s"$centralBase/commons-logging/commons-logging/1.1.3/commons-logging-1.1.3-bin.zip"
      )

      'tarGz - {
        * - {
          if (isActualCentral)
            withArtifacts(mod, version, "tar.gz", classifierOpt = Some("bin"), transitive = true) { artifacts =>
              assert(artifacts.length == 2)
              val urls = artifacts.map(_.url).toSet
              assert(urls == expectedTarGzArtifactUrls)
            }
        }
        * - {
          withArtifacts(mod, version, "tar.gz", attributes = Attributes("tar.gz", "bin"), classifierOpt = Some("bin"), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainTarGzUrl))
          }
        }
      }

      'zip - {
        * - {
          if (isActualCentral)
            withArtifacts(mod, version, "zip", classifierOpt = Some("bin"), transitive = true) { artifacts =>
              assert(artifacts.length == 2)
              val urls = artifacts.map(_.url).toSet
              assert(urls == expectedZipArtifactUrls)
            }
        }
        * - {
          withArtifacts(mod, version, "zip", attributes = Attributes("zip", "bin"), classifierOpt = Some("bin"), transitive = true) { artifacts =>
            assert(artifacts.nonEmpty)
            val urls = artifacts.map(_.url).toSet
            assert(urls.contains(mainZipUrl))
          }
        }
      }
    }

    'groupIdVersionProperties - {
      resolutionCheck(
        Module("org.apache.directory.shared", "shared-ldap"),
        "0.9.19"
      )
    }

    'relocation - {
      * - resolutionCheck(
        Module("bouncycastle", "bctsp-jdk14"),
        "138"
      )

      'ignoreRelocationJars - {
        val mod = Module("org.apache.commons", "commons-io")
        val ver = "1.3.2"

        val expectedUrl = s"$centralBase/commons-io/commons-io/1.3.2/commons-io-1.3.2.jar"

        * - resolutionCheck(mod, ver)

        * - withArtifacts(mod, ver, "jar", transitive = true) { artifacts =>
          assert(artifacts.length == 1)
          assert(artifacts.head.url == expectedUrl)
        }
      }
    }

    'entities - {
      'odash - resolutionCheck(
        Module("org.codehaus.plexus", "plexus"),
        "1.0.4"
      )
    }

    'parentBeforeImports - {
      * - resolutionCheck(
        Module("org.kie", "kie-api"),
        "6.5.0.Final",
        extraRepos = Seq(MavenRepository("https://repository.jboss.org/nexus/content/repositories/public"))
      )
    }

    'signaturesOfSignatures - {
      val mod = Module("org.yaml", "snakeyaml")
      val ver = "1.17"

      def hasSha1(a: Artifact) = a.checksumUrls.contains("SHA-1")
      def hasMd5(a: Artifact) = a.checksumUrls.contains("MD5")
      def hasSig(a: Artifact) = a.extra.contains("sig")
      def sigHasSig(a: Artifact) = a.extra.get("sig").exists(hasSig)

      * - resolutionCheck(mod, ver)

      * - withArtifacts(mod, ver, "*") { artifacts =>

        val jarOpt = artifacts.find(_.`type` == "bundle").orElse(artifacts.find(_.`type` == "jar"))
        val pomOpt = artifacts.find(_.`type` == "pom")

        assert(jarOpt.nonEmpty)
        assert(jarOpt.forall(hasSha1))
        assert(jarOpt.forall(hasMd5))
        assert(jarOpt.forall(hasSig))

        if (isActualCentral) {
          if (artifacts.length != 2 || jarOpt.isEmpty || pomOpt.isEmpty)
            artifacts.foreach(println)

          assert(jarOpt.forall(_.`type` == "bundle"))
          assert(artifacts.length == 2)
          assert(pomOpt.nonEmpty)
          assert(pomOpt.forall(hasSha1))
          assert(pomOpt.forall(hasMd5))
          assert(pomOpt.forall(hasSig))
          assert(jarOpt.forall(sigHasSig))
          assert(pomOpt.forall(sigHasSig))
        }
      }
    }

    'sbtPluginVersionRange - {
      val mod = Module("org.ensime", "sbt-ensime", attributes = Map("scalaVersion" -> "2.10", "sbtVersion" -> "0.13"))
      val ver = "1.12.+"

      * - {
        if (isActualCentral) // doesn't work via proxies, which don't list all the upstream available versions
          resolutionCheck(mod, ver)
      }
    }

    'multiVersionRanges - {
      val mod = Module("org.webjars.bower", "dgrid")
      val ver = "1.0.0"

      * - {
        if (isActualCentral) // if false, the tests rely on things straight from Central, which can be updated sometimes…
          resolutionCheck(mod, ver)
      }
    }

    'dependencyManagementScopeOverriding - {
      val mod = Module("org.apache.tika", "tika-app")
      val ver = "1.13"

      * - resolutionCheck(mod, ver)
    }

    'optionalArtifacts - {
      val mod = Module("io.monix", "monix_2.12")
      val ver = "2.3.0"

      val mainUrl = "https://repo1.maven.org/maven2/io/monix/monix_2.12/2.3.0/monix_2.12-2.3.0.jar"

      * - resolutionCheck(mod, ver)

      * - {
        if (isActualCentral)
          withArtifacts(mod, ver, "jar") { artifacts =>
            val mainArtifactOpt = artifacts.find(_.url == mainUrl)
            assert(mainArtifactOpt.nonEmpty)
            assert(mainArtifactOpt.forall(_.isOptional))
          }
      }

      * - withArtifacts(mod, ver, "jar", optional = false) { artifacts =>
        val mainArtifactOpt = artifacts.find(_.url == mainUrl)
        assert(mainArtifactOpt.isEmpty)
      }

      * - {
        if (isActualCentral)
          withArtifacts(Module("com.lihaoyi", "scalatags_2.12"), "0.6.2", "jar", transitive = true, optional = false) { artifacts =>

            assert(artifacts.forall(!_.isOptional))

            val urls = artifacts.map(_.url).toSet

            val expectedUrls = Set(
              "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.0/scala-library-2.12.0.jar",
              "https://repo1.maven.org/maven2/com/lihaoyi/sourcecode_2.12/0.1.3/sourcecode_2.12-0.1.3.jar",
              "https://repo1.maven.org/maven2/com/lihaoyi/scalatags_2.12/0.6.2/scalatags_2.12-0.6.2.jar"
            )
            assert(urls == expectedUrls)
          }
      }
    }

    'packagingTpe - {
      val mod = Module("android.arch.lifecycle", "extensions")
      val ver = "1.0.0-alpha3"

      val extraRepo = MavenRepository("https://maven.google.com")

      * - resolutionCheck(mod, ver, extraRepos = Seq(extraRepo))

      * - withArtifacts(mod, ver, "*", extraRepos = Seq(extraRepo), transitive = true) { artifacts =>
        val urls = artifacts.map(_.url).toSet
        val expectedUrls = Set(
          "https://maven.google.com/com/android/support/support-fragment/25.3.1/support-fragment-25.3.1.aar",
          "https://maven.google.com/android/arch/core/core/1.0.0-alpha3/core-1.0.0-alpha3.aar",
          "https://maven.google.com/android/arch/lifecycle/runtime/1.0.0-alpha3/runtime-1.0.0-alpha3.aar",
          "https://maven.google.com/android/arch/lifecycle/extensions/1.0.0-alpha3/extensions-1.0.0-alpha3.aar",
          "https://maven.google.com/com/android/support/support-compat/25.3.1/support-compat-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-media-compat/25.3.1/support-media-compat-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-core-ui/25.3.1/support-core-ui-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-core-utils/25.3.1/support-core-utils-25.3.1.aar",
          "https://maven.google.com/com/android/support/support-annotations/25.3.1/support-annotations-25.3.1.jar",
          "https://maven.google.com/android/arch/lifecycle/common/1.0.0-alpha3/common-1.0.0-alpha3.jar"
        )

        assert(expectedUrls.forall(urls))
      }
    }

    'noArtifactIdExclusion - {
      val mod = Module("org.datavec", "datavec-api")
      val ver = "0.9.1"

      * - resolutionCheck(mod, ver)
    }

    'snapshotVersioningBundlePackaging - {
      val mod = Module("org.talend.daikon", "daikon")
      val ver = "0.19.0-SNAPSHOT"

      val extraRepos = Seq(
        MavenRepository("https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceRelease"),
        MavenRepository("https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceSnapshot")
      )

      * - resolutionCheck(mod, ver, extraRepos = extraRepos)

      * - {
        if (isActualCentral)
          withArtifacts(mod, ver, "*", extraRepos = extraRepos, transitive = true) { artifacts =>
            val urls = artifacts.map(_.url).toSet
            val expectedUrls = Set(
              "https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceRelease/com/cedarsoftware/json-io/4.9.9-TALEND/json-io-4.9.9-TALEND.jar",
              "https://artifacts-oss.talend.com/nexus/content/repositories/TalendOpenSourceSnapshot/org/talend/daikon/daikon/0.19.0-SNAPSHOT/daikon-0.19.0-20171201.100416-43.jar",
              "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.5.3/jackson-annotations-2.5.3.jar",
              "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar",
              "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.5.3/jackson-databind-2.5.3.jar",
              "https://repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.7/paranamer-2.7.jar",
              "https://repo1.maven.org/maven2/commons-codec/commons-codec/1.6/commons-codec-1.6.jar",
              "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar",
              "https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar",
              "https://repo1.maven.org/maven2/org/apache/avro/avro/1.8.1/avro-1.8.1.jar",
              "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar",
              "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar",
              "https://repo1.maven.org/maven2/org/codehaus/jackson/jackson-core-asl/1.9.13/jackson-core-asl-1.9.13.jar",
              "https://repo1.maven.org/maven2/org/codehaus/jackson/jackson-mapper-asl/1.9.13/jackson-mapper-asl-1.9.13.jar",
              "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.12/slf4j-api-1.7.12.jar",
              "https://repo1.maven.org/maven2/org/tukaani/xz/1.5/xz-1.5.jar",
              "https://repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.1.3/snappy-java-1.1.1.3.jar"
            )

            assert(expectedUrls.forall(urls))
          }
      }
    }
  }

}
