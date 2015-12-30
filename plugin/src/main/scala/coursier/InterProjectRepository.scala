package coursier

import scalaz.{ -\/, \/-, Monad, EitherT }

case class InterProjectSource(artifacts: Map[(Module, String), Map[String, Seq[Artifact]]]) extends Artifact.Source {
  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[String]]
  ): Seq[Artifact] =
    overrideClassifiers match {
      case None =>
        artifacts
          .get(dependency.moduleVersion)
          .toSeq
          .flatMap(_.get(dependency.configuration))
          .flatten
      case Some(_) =>
        Nil
    }
}

case class InterProjectRepository(projects: Seq[(Project, Seq[(String, Seq[Artifact])])]) extends Repository {

  private val map = projects
    .map { case (proj, _) => proj.moduleVersion -> proj }
    .toMap

  val source = InterProjectSource(
    projects.map { case (proj, artifactsByConfig) =>
      val artifacts = artifactsByConfig.toMap
      val allArtifacts = proj.allConfigurations.map { case (config, extends0) =>
        config -> extends0.toSeq.flatMap(artifacts.getOrElse(_, Nil))
      }
      proj.moduleVersion -> allArtifacts
    }.toMap
  )

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {
    val res = map.get((module, version)) match {
      case Some(proj) =>
        \/-((source, proj))
      case None =>
        -\/("Not found")
    }

    EitherT(F.point(res))
  }
}