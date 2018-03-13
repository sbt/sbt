package coursier.cli.scaladex

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService

import argonaut._, Argonaut._, ArgonautShapeless._
import coursier.core.{Artifact, Attributes}
import coursier.interop.scalaz._
import coursier.util.{EitherT, Gather}
import coursier.{Fetch, Module}
import scalaz.concurrent.Task

object Scaladex {

  case class SearchResult(
    /** GitHub organization */
    organization: String,
    /** GitHub repository */
    repository: String,
    /** Scaladex artifact names */
    artifacts: List[String] = Nil
  )

  case class ArtifactInfos(
    /** Dependency group ID (aka organization) */
    groupId: String,
    /** Dependency artifact ID (aka name or module name) */
    artifactId: String,
    /** Dependency version */
    version: String
  )

  def apply(pool: ExecutorService): Scaladex[Task] =
    Scaladex({ url =>
      EitherT(Task[Either[String, String]]({
        var conn: HttpURLConnection = null

        val b = try {
          conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
          coursier.internal.FileUtil.readFully(conn.getInputStream)
        } finally {
          if (conn != null)
            coursier.Cache.closeConn(conn)
        }

        Right(new String(b, StandardCharsets.UTF_8))
      })(pool))
    }, Gather[Task])

  def cached(fetch: Fetch.Content[Task]*): Scaladex[Task] =
    Scaladex({
      url =>
        def get(fetch: Fetch.Content[Task]) =
          fetch(
            Artifact(url, Map(), Map(), Attributes("", ""), changing = true, None)
          )

        (get(fetch.head) /: fetch.tail)(_ orElse get(_))
    }, Gather[Task])
}

// TODO Add F[_] type param, change `fetch` type to `String => EitherT[F, String, String]`, adjust method signatures accordingly, ...
case class Scaladex[F[_]](fetch: String => EitherT[F, String, String], G: Gather[F]) {

  private implicit val G0 = G

  // quick & dirty API for querying scaladex

  def search(name: String, target: String, scalaVersion: String): EitherT[F, String, Seq[Scaladex.SearchResult]] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/search?q=$name&target=$target&scalaVersion=$scalaVersion"
    )

    s.flatMap(s => EitherT.fromEither(s.decodeEither[List[Scaladex.SearchResult]]))
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @param artifactName: Scaladex artifact name
    * @return
    */
  def artifactInfos(organization: String, repository: String, artifactName: String): EitherT[F, String, Scaladex.ArtifactInfos] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/project?organization=$organization&repository=$repository&artifact=$artifactName"
    )

    s.flatMap(s => EitherT.fromEither(s.decodeEither[Scaladex.ArtifactInfos]))
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @return
    */
  def artifactNames(organization: String, repository: String): EitherT[F, String, Seq[String]] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/project?organization=$organization&repository=$repository"
    )

    case class Result(artifacts: List[String])

    s.flatMap(s => EitherT.fromEither(s.decodeEither[Result].map(_.artifacts)))
  }


  /**
    * Modules / versions known to the Scaladex
    *
    * Latest version only.
    */
  def dependencies(name: String, scalaVersion: String, logger: String => Unit): EitherT[F, String, Seq[(Module, String)]] = {
    val idx = name.indexOf('/')
    val orgNameOrError =
      if (idx >= 0) {
        val org = name.take(idx)
        val repo = name.drop(idx + 1)

        artifactNames(org, repo).map((org, repo, _)): EitherT[F, String, (String, String, Seq[String])]
      } else
        search(name, "JVM", scalaVersion) // FIXME Don't hardcode
          .flatMap {
            case Seq(first, _*) =>
              logger(s"Using ${first.organization}/${first.repository} for $name")
              EitherT.fromEither[F](Right((first.organization, first.repository, first.artifacts)): Either[String, (String, String, Seq[String])])
            case Seq() =>
              EitherT.fromEither[F](Left(s"No project found for $name"): Either[String, (String, String, Seq[String])])
          }

    orgNameOrError.flatMap {
      case (ghOrg, ghRepo, artifactNames) =>

        val moduleVersions = G.map(G.gather(artifactNames.map { artifactName =>
          G.map(artifactInfos(ghOrg, ghRepo, artifactName).run) {
            case Left(err) =>
              logger(s"Cannot get infos about artifact $artifactName from $ghOrg/$ghRepo: $err, ignoring it")
              Nil
            case Right(infos) =>
              logger(s"Found module ${infos.groupId}:${infos.artifactId}:${infos.version}")
              Seq(Module(infos.groupId, infos.artifactId) -> infos.version)
          }
        }))(_.flatten)

        EitherT(G.map(moduleVersions) { l =>
          if (l.isEmpty)
            Left(s"No module found for $ghOrg/$ghRepo")
          else
            Right(l)
        })
    }
  }

}
