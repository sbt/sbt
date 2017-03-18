package coursier.cli.scaladex

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService

import argonaut._, Argonaut._, ArgonautShapeless._
import coursier.core.{ Artifact, Attributes }
import coursier.{ Fetch, Module }

import scalaz.{ -\/, EitherT, Monad, Nondeterminism, \/, \/- }
import scalaz.Scalaz.ToEitherOps
import scalaz.Scalaz.ToEitherOpsFromEither
import scalaz.concurrent.Task
import scalaz.std.list._

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
      EitherT(Task({
        var conn: HttpURLConnection = null

        val b = try {
          conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
          coursier.Platform.readFullySync(conn.getInputStream)
        } finally {
          if (conn != null)
            conn.disconnect()
        }

        new String(b, StandardCharsets.UTF_8).right[String]
      })(pool))
    }, Nondeterminism[Task])

  def cached(fetch: Fetch.Content[Task]*): Scaladex[Task] =
    Scaladex({
      url =>
        def get(fetch: Fetch.Content[Task]) =
          fetch(
            Artifact(url, Map(), Map(), Attributes("", ""), changing = true, None)
          )

        (get(fetch.head) /: fetch.tail)(_ orElse get(_))
    }, Nondeterminism[Task])
}

// TODO Add F[_] type param, change `fetch` type to `String => EitherT[F, String, String]`, adjust method signatures accordingly, ...
case class Scaladex[F[_]](fetch: String => EitherT[F, String, String], F: Nondeterminism[F]) {

  private implicit def F0 = F

  // quick & dirty API for querying scaladex

  def search(name: String, target: String, scalaVersion: String): EitherT[F, String, Seq[Scaladex.SearchResult]] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/search?q=$name&target=$target&scalaVersion=$scalaVersion"
    )

    s.flatMap(s => EitherT.fromDisjunction[F](s.decodeEither[List[Scaladex.SearchResult]].disjunction))
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

    s.flatMap(s => EitherT.fromDisjunction[F](s.decodeEither[Scaladex.ArtifactInfos].disjunction))
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

    s.flatMap(s => EitherT.fromDisjunction[F](s.decodeEither[Result].disjunction.map(_.artifacts)))
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
              EitherT.fromDisjunction[F]((first.organization, first.repository, first.artifacts).right): EitherT[F, String, (String, String, Seq[String])]
            case Seq() =>
              EitherT.fromDisjunction[F](s"No project found for $name".left): EitherT[F, String, (String, String, Seq[String])]
          }

    orgNameOrError.flatMap {
      case (ghOrg, ghRepo, artifactNames) =>

        val moduleVersions = F.map(F.gather(artifactNames.map { artifactName =>
          F.map(artifactInfos(ghOrg, ghRepo, artifactName).run) {
            case -\/(err) =>
              logger(s"Cannot get infos about artifact $artifactName from $ghOrg/$ghRepo: $err, ignoring it")
              Nil
            case \/-(infos) =>
              logger(s"Found module ${infos.groupId}:${infos.artifactId}:${infos.version}")
              Seq(Module(infos.groupId, infos.artifactId) -> infos.version)
          }
        }))(_.flatten)

        EitherT(F.map(moduleVersions) { l =>
          if (l.isEmpty)
            s"No module found for $ghOrg/$ghRepo".left
          else
            l.right
        })
    }
  }

}
