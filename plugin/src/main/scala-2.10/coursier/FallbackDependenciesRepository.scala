package coursier

import java.net.URL

import scalaz.{ EitherT, Monad }

case class FallbackDependenciesRepository(
  fallbacks: Map[(Module, String), (URL, Boolean)]
) extends Repository {

  private val source = new Artifact.Source {
    def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[String]]
    ) =
      fallbacks.get(dependency.moduleVersion) match {
        case None => Nil
        case Some((url, changing)) =>
          Seq(
            Artifact(url.toString, Map.empty, Map.empty, Attributes("jar", ""), changing, None)
          )
      }
  }

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Fetch.Content[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] =
    fallbacks.get((module, version)) match {
      case None =>
        EitherT.left(F.point("No fallback URL found"))

      case Some((url, _)) =>
        val proj = Project(
          module,
          version,
          Nil,
          Map.empty,
          None,
          Nil,
          Nil,
          Nil,
          None,
          None,
          None,
          Nil,
          Info.empty
        )

        EitherT.right(F.point((source, proj)))
    }
}
