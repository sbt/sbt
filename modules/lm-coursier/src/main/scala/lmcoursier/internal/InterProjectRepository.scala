package lmcoursier.internal

import coursier.core._
import coursier.util.{EitherT, Monad}

// private[coursier]
final case class InterProjectRepository(projects: Seq[Project]) extends Repository {

  private val map = projects
    .map(proj => proj.moduleVersion -> proj)
    .toMap

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] = {

    val res = map
      .get((module, version))
      .map((this, _))
      .toRight("Not found")

    EitherT(F.point(res))
  }

  override def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ) =
    Nil
}