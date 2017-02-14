package coursier

import scala.language.higherKinds

import scalaz.{ -\/, \/-, Monad, EitherT }

final case class InterProjectRepository(projects: Seq[Project]) extends Repository {

  private val map = projects
    .map { proj => proj.moduleVersion -> proj }
    .toMap

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {
    val res = map.get((module, version)) match {
      case Some(proj) =>
        \/-((Artifact.Source.empty, proj))
      case None =>
        -\/("Not found")
    }

    EitherT(F.point(res))
  }
}