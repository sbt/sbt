package coursier
package test

import coursier.core._

import scalaz.{ Monad, EitherT }
import scalaz.Scalaz._

class TestRepository(projects: Map[(Module, String), Project]) extends Repository {
  val source = new core.Artifact.Source {
    def artifacts(dependency: Dependency, project: Project) = ???
  }
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ) =
    EitherT(F.point(
      projects.get((module, version)).map((source, _)).toRightDisjunction("Not found")
    ))
}
