package coursier

import scala.language.higherKinds

import scalaz.{EitherT, Monad}
import scalaz.syntax.monad._
import scalaz.syntax.std.option._

final case class InterProjectRepository(projects: Seq[Project]) extends Repository {

  private val map = projects
    .map(proj => proj.moduleVersion -> proj)
    .toMap

  def find[F[_]: Monad](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val res = map
      .get((module, version))
      .toRightDisjunction("Not found")
      .map((Artifact.Source.empty, _))

    EitherT(res.point)
  }
}