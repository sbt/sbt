package coursier
package test

import coursier.core._
import coursier.util.{EitherT, Monad}

final case class TestRepository(projects: Map[(Module, String), Project]) extends Repository {
  val source = new core.Artifact.Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[String]]
    ) = ???
  }
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ) =
    EitherT(
      F.point(
        projects.get((module, version)).map((source, _)).toRight("Not found")
      )
    )
}
