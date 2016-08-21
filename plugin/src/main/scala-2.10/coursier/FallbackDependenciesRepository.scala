package coursier

import java.net.URL

import scalaz.{ EitherT, Monad }

case class FallbackDependenciesRepository(
  fallbacks: Map[(Module, String), (URL, Boolean)]
) extends Repository {

  private val source: Artifact.Source = new Artifact.Source {
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

        val urlStr = url.toExternalForm
        val idx = urlStr.lastIndexOf('/')

        if (idx < 0 || urlStr.endsWith("/"))
          EitherT.left(F.point(s"$url doesn't point to a file"))
        else {
          val (dirUrlStr, fileName) = urlStr.splitAt(idx + 1)

          fetch(Artifact(dirUrlStr, Map.empty, Map.empty, Attributes("", ""), changing = true, None)).flatMap { listing =>

            val files = coursier.core.compatibility.listWebPageFiles(dirUrlStr, listing)

            if (files.contains(fileName)) {

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
            } else
              EitherT.left(F.point(s"$fileName not found under $dirUrlStr"))
          }
        }
    }
}
