package coursier

import java.io.{ File, FileNotFoundException, IOException }
import java.net.{ HttpURLConnection, URL, URLConnection }

import scala.language.higherKinds

import scalaz.{ EitherT, Monad }

object FallbackDependenciesRepository {

  def exists(url: URL): Boolean = {

    // Sometimes HEAD attempts fail even though standard GETs are fine.
    // E.g. https://github.com/NetLogo/NetLogo/releases/download/5.3.1/NetLogo.jar
    // returning 403s. Hence the second attempt below.

    val firstAttemptOpt = url.getProtocol match {
      case "file" =>
        Some(new File(url.getPath).exists()) // FIXME Escaping / de-escaping needed here?

      case "http" | "https" =>

        // HEAD request attempt, adapted from http://stackoverflow.com/questions/22541629/android-how-can-i-make-an-http-head-request/22545275#22545275

        var conn: HttpURLConnection = null
        try {
          conn = url
            .openConnection()
            .asInstanceOf[HttpURLConnection]
          conn.setRequestMethod("HEAD")
          conn.getInputStream.close()
          Some(true)
        } catch {
          case _: FileNotFoundException =>
            Some(false)
          case _: IOException => // error other than not found
            None
        } finally {
          if (conn != null)
            conn.disconnect()
        }
      case _ =>
        None
    }

    firstAttemptOpt.getOrElse {
      var conn: URLConnection = null
      try {
        conn = url.openConnection()
        // NOT setting request type to HEAD here.
        conn.getInputStream.close()
        true
      } catch {
        case _: IOException =>
          false
      } finally {
        conn match {
          case conn0: HttpURLConnection => conn0.disconnect()
          case _ =>
        }
      }
    }
  }

}

final case class FallbackDependenciesRepository(
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

          // Not sure F.point will make that run like Task.apply would have
          // if F = Task
          EitherT.right(F.point(FallbackDependenciesRepository.exists(url))).flatMap { exists =>
            if (exists) {
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
