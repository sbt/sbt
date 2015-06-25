package coursier
package test

import coursier.core.Repository
import utest._
import scala.async.Async.{async, await}

import coursier.test.compatibility._

object CentralTests extends TestSuite {

  val repositories = Seq[Repository](
    Repository.mavenCentral
  )

  def resolve(deps: Set[Dependency], filter: Option[Dependency => Boolean] = None) = {
    ResolutionProcess(Resolution(deps, filter = filter))
      .run(Repository.fetchSeveralFrom(repositories))
      .runF
  }

  def repr(dep: Dependency) =
    s"${dep.module.organization}:${dep.module.name}:${dep.attributes.`type`}:${Some(dep.attributes.classifier).filter(_.nonEmpty).map(_+":").mkString}${dep.version}"

  def resolutionCheck(module: Module, version: String) =
    async {
      val expected = await(textResource(s"resolutions/${module.organization}:${module.name}:$version")).split('\n').toSeq

      val dep = Dependency(module, version)
      val res = await(resolve(Set(dep)))

      val result = res.dependencies.toVector.map(repr).sorted.distinct

      for (((e, r), idx) <- expected.zip(result).zipWithIndex if e != r)
        println(s"Line $idx:\n  expected: $e\n  got:$r")

      assert(result == expected)
    }

  val tests = TestSuite {
    'logback{
      async {
        val dep = Dependency(Module("ch.qos.logback", "logback-classic"), "1.1.3")
        val res = await(resolve(Set(dep)))
          .copy(projectCache = Map.empty, errorCache = Map.empty) // No validating these here

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module("ch.qos.logback", "logback-core"), "1.1.3").withCompileScope,
            Dependency(Module("org.slf4j", "slf4j-api"), "1.7.7").withCompileScope))

        assert(res == expected)
      }
    }
    'asm{
      async {
        val dep = Dependency(Module("org.ow2.asm", "asm-commons"), "5.0.2")
        val res = await(resolve(Set(dep)))
          .copy(projectCache = Map.empty, errorCache = Map.empty) // No validating these here

        val expected = Resolution(
          rootDependencies = Set(dep),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module("org.ow2.asm", "asm-tree"), "5.0.2").withCompileScope,
            Dependency(Module("org.ow2.asm", "asm"), "5.0.2").withCompileScope))

        assert(res == expected)
      }
    }
    'jodaVersionInterval{
      async {
        val dep = Dependency(Module("joda-time", "joda-time"), "[2.2,2.8]")
        val res0 = await(resolve(Set(dep)))
        val res = res0.copy(projectCache = Map.empty, errorCache = Map.empty)

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
    'spark{
      resolutionCheck(Module("org.apache.spark", "spark-core_2.11"), "1.3.1")
    }
    'argonautShapeless{
      resolutionCheck(Module("com.github.alexarchambault", "argonaut-shapeless_6.1_2.11"), "0.2.0")
    }
  }

}
