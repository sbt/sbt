package coursier

import coursier.util.EitherT

import scala.language.higherKinds
import scalaz.Monad

final case class InterProjectRepository(projects: Seq[Project]) extends Repository {

  private val map = projects
    .map(proj => proj.moduleVersion -> proj)
    .toMap

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val res = map
      .get((module, version))
      .map((Artifact.Source.empty, _))
      .toRight("Not found")

    EitherT(F.point(res))
  }
}