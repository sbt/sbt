package coursier
package test

import coursier.core._

import scalaz.EitherT
import scalaz.concurrent.Task
import scalaz.Scalaz._

class TestRepository(projects: Map[(Module, String), Project]) extends Repository {
  val source = new core.Artifact.Source {
    def artifacts(dependency: Dependency, project: Project) = ???
  }
  def find(module: Module, version: String, cachePolicy: Repository.CachePolicy) =
    EitherT(Task.now(
      projects.get((module, version)).map((source, _)).toRightDisjunction("Not found")
    ))
}
