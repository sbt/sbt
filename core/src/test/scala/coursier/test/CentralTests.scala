package coursier
package test

import utest._
import scala.async.Async.{async, await}

import coursier.test.compatibility._

object CentralTests extends TestSuite {

  val repositories = Seq[Repository](
    repository.mavenCentral
  )

  val tests = TestSuite {
    'logback{
      async {
        val dep = Dependency(Module("ch.qos.logback", "logback-classic", "1.1.3"))
        val res0 =
          await(resolve(Set(dep), fetchFrom(repositories))
            .runF)

        val res = res0.copy(
          projectsCache = Map.empty, errors = Map.empty, // No validating these here
          dependencies = res0.dependencies.filter(dep => dep.scope == Scope.Compile && !dep.optional)
        )

        val expected = Resolution(
          rootDependencies = Set(dep.withCompileScope),
          dependencies = Set(
            dep.withCompileScope,
            Dependency(Module("ch.qos.logback", "logback-core", "1.1.3")).withCompileScope,
            Dependency(Module("org.slf4j", "slf4j-api", "1.7.7")).withCompileScope
          )
        )

        assert(res == expected)
      }
    }
  }

}
