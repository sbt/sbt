package lmcoursier.internal

import java.io.{ File, FileNotFoundException, IOException }
import java.net.{ HttpURLConnection, URI, URLConnection }

import coursier.cache.{ ConnectionBuilder, FileCache }
import coursier.core.*
import coursier.util.{ Artifact, EitherT, Monad }

import scala.annotation.nowarn
import scala.util.Try

object TemporaryInMemoryRepository {

  def closeConn(conn: URLConnection): Unit = {
    Try(conn.getInputStream).toOption.filter(_ != null).foreach(_.close())
    conn match {
      case conn0: HttpURLConnection =>
        Try(conn0.getErrorStream).toOption.filter(_ != null).foreach(_.close())
        conn0.disconnect()
      case _ =>
    }
  }

  def exists(
      uri: URI,
      localArtifactsShouldBeCached: Boolean
  ): Boolean =
    exists(uri, localArtifactsShouldBeCached, None)

  def exists(
      uri: URI,
      localArtifactsShouldBeCached: Boolean,
      cacheOpt: Option[FileCache[Nothing]]
  ): Boolean = {

    // Sometimes HEAD attempts fail even though standard GETs are fine.
    // E.g. https://github.com/NetLogo/NetLogo/releases/download/5.3.1/NetLogo.jar
    // returning 403s. Hence the second attempt below.

    val protocolSpecificAttemptOpt = {

      def ifFile: Option[Boolean] = {
        if (localArtifactsShouldBeCached && !new File(uri).exists()) {
          val cachePath = coursier.cache.CacheDefaults.location
          // 'file' here stands for the protocol (e.g. it's https instead for https:// URLs)
          Some(new File(cachePath, s"file/${uri.getPath}").exists())
        } else {
          Some(new File(uri).exists()) // FIXME Escaping / de-escaping needed here?
        }
      }

      def ifHttp: Option[Boolean] = {
        // HEAD request attempt, adapted from http://stackoverflow.com/questions/22541629/android-how-can-i-make-an-http-head-request/22545275#22545275

        var conn: URLConnection = null
        try {
          conn = ConnectionBuilder(uri.toASCIIString)
            .withFollowHttpToHttpsRedirections(
              cacheOpt.fold(false)(_.followHttpToHttpsRedirections)
            )
            .withFollowHttpsToHttpRedirections(
              cacheOpt.fold(false)(_.followHttpsToHttpRedirections)
            )
            .withSslSocketFactoryOpt(cacheOpt.flatMap(_.sslSocketFactoryOpt))
            .withHostnameVerifierOpt(cacheOpt.flatMap(_.hostnameVerifierOpt))
            .withMethod("HEAD")
            .withMaxRedirectionsOpt(cacheOpt.flatMap(_.maxRedirections))
            .connection()
          // Even though the finally clause handles this too, this has to be run here, so that we return Some(true)
          // iff this doesn't throw.
          conn.getInputStream.close()
          Some(true)
        } catch {
          case _: FileNotFoundException => Some(false)
          case _: IOException           => None // error other than not found
        } finally {
          if (conn != null)
            closeConn(conn)
        }
      }

      uri.getScheme match {
        case "file"           => ifFile
        case "http" | "https" => ifHttp
        case _                => None
      }
    }

    def genericAttempt: Boolean = {
      var conn: URLConnection = null
      try {
        conn = uri.toURL.openConnection()
        // NOT setting request type to HEAD here.
        conn.getInputStream.close()
        true
      } catch {
        case _: IOException => false
      } finally {
        if (conn != null)
          closeConn(conn)
      }
    }

    protocolSpecificAttemptOpt
      .getOrElse(genericAttempt)
  }

  def apply(
      fallbacks: Map[(Module, String), (URI, Boolean)]
  ): TemporaryInMemoryRepository =
    new TemporaryInMemoryRepository(fallbacks, localArtifactsShouldBeCached = false, None)

  def apply(
      fallbacks: Map[(Module, String), (URI, Boolean)],
      localArtifactsShouldBeCached: Boolean
  ): TemporaryInMemoryRepository =
    new TemporaryInMemoryRepository(fallbacks, localArtifactsShouldBeCached, None)

  def apply[F[_]](
      fallbacks: Map[(Module, String), (URI, Boolean)],
      cache: FileCache[F]
  ): TemporaryInMemoryRepository =
    new TemporaryInMemoryRepository(
      fallbacks,
      localArtifactsShouldBeCached = cache.localArtifactsShouldBeCached,
      Some(cache.asInstanceOf[FileCache[Nothing]])
    )

}

final class TemporaryInMemoryRepository private (
    val fallbacks: Map[(Module, String), (URI, Boolean)],
    val localArtifactsShouldBeCached: Boolean,
    val cacheOpt: Option[FileCache[Nothing]]
) extends Repository {

  @nowarn
  def find[F[_]](
      module: Module,
      version: String,
      fetch: Repository.Fetch[F]
  )(using
      F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] = {

    def res = fallbacks
      .get((module, version))
      .fold[Either[String, (ArtifactSource, Project)]](Left("No fallback URL found")) {
        case (uri, _) =>
          val urlStr = uri.toURL.toExternalForm
          val idx = urlStr.lastIndexOf('/')

          if (idx < 0 || urlStr.endsWith("/"))
            Left(s"$uri doesn't point to a file")
          else {
            val (dirUrlStr, fileName) = urlStr.splitAt(idx + 1)

            if (TemporaryInMemoryRepository.exists(uri, localArtifactsShouldBeCached, cacheOpt)) {
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
                relocated = false,
                None,
                Nil,
                Info.empty
              )

              Right((this, proj))
            } else
              Left(s"$fileName not found under $dirUrlStr")
          }
      }

    // EitherT(F.bind(F.point(()))(_ => F.point(res)))
    EitherT(F.map(F.point(()))(_ => res))
  }

  @nowarn
  def artifacts(
      dependency: Dependency,
      project: Project,
      overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] = {
    fallbacks
      .get(dependency.moduleVersion)
      .toSeq
      .map { (url, changing) =>
        val url0 = url.toString
        val ext = url0.substring(url0.lastIndexOf('.') + 1)
        val pub = Publication(
          dependency.module.name.value, // ???
          Type(ext),
          Extension(ext),
          Classifier.empty
        )
        (pub, Artifact(url0, Map.empty, Map.empty, changing, optional = false, None))
      }
  }

}
