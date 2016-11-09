package coursier.cli.scaladex

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

import argonaut._, Argonaut._, ArgonautShapeless._
import coursier.core.{ Artifact, Attributes }
import coursier.{ Fetch, Module }

import scalaz.{-\/, \/, \/-}
import scalaz.Scalaz.ToEitherOps
import scalaz.Scalaz.ToEitherOpsFromEither
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

  def apply(): Scaladex =
    Scaladex { url =>
      var conn: HttpURLConnection = null

      val b = try {
        conn = new java.net.URL(url).openConnection().asInstanceOf[HttpURLConnection]
        coursier.Platform.readFullySync(conn.getInputStream)
      } finally {
        if (conn != null)
          conn.disconnect()
      }

      new String(b, StandardCharsets.UTF_8)
    }

  def cached(fetch: Fetch.Content[Task]*): Scaladex =
    Scaladex {
      url =>
        def get(fetch: Fetch.Content[Task]) =
          fetch(
            Artifact(url, Map(), Map(), Attributes("", ""), changing = true, None)
          )

        (get(fetch.head) /: fetch.tail)(_ orElse get(_)).run.unsafePerformSync match {
          case -\/(err) =>
            throw new Exception(s"Fetching $url: $err")
          case \/-(s) => s
        }
    }
}

// TODO Add F[_] type param, change `fetch` type to `String => EitherT[F, String, String]`, adjust method signatures accordingly, ...
case class Scaladex(fetch: String => String) {

  // quick & dirty API for querying scaladex

  def search(name: String, target: String, scalaVersion: String): String \/ Seq[Scaladex.SearchResult] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/search?q=$name&target=$target&scalaVersion=$scalaVersion"
    )

    s.decodeEither[List[Scaladex.SearchResult]].disjunction
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @param artifactName: Scaladex artifact name
    * @return
    */
  def artifactInfos(organization: String, repository: String, artifactName: String): String \/ Scaladex.ArtifactInfos = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/project?organization=$organization&repository=$repository&artifact=$artifactName"
    )

    s.decodeEither[Scaladex.ArtifactInfos].disjunction
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @return
    */
  def artifactNames(organization: String, repository: String): String \/ Seq[String] = {

    val s = fetch(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/project?organization=$organization&repository=$repository"
    )

    case class Result(artifacts: List[String])

    s.decodeEither[Result].disjunction.map(_.artifacts)
  }


  /**
    * Modules / versions known to the Scaladex
    *
    * Latest version only.
    */
  def dependencies(name: String, scalaVersion: String, logger: String => Unit): String \/ Seq[(Module, String)] = {

    val idx = name.indexOf('/')
    val orgNameOrError =
      if (idx >= 0) {
        val org = name.take(idx)
        val repo = name.drop(idx + 1)

        artifactNames(org, repo).map((org, repo, _))
      } else
        search(name, "JVM", scalaVersion) // FIXME Don't hardcode
          .flatMap {
            case Seq(first, _*) =>
              logger(s"Using ${first.organization}/${first.repository} for $name")
              (first.organization, first.repository, first.artifacts).right
            case Seq() =>
              s"No project found for $name".left
          }

    orgNameOrError.flatMap {
      case (ghOrg, ghRepo, artifactNames) =>

        val moduleVersions = artifactNames.flatMap { artifactName =>
          artifactInfos(ghOrg, ghRepo, artifactName) match {
            case -\/(err) =>
              logger(s"Cannot get infos about artifact $artifactName from $ghOrg/$ghRepo: $err, ignoring it")
              Nil
            case \/-(infos) =>
              logger(s"Found module ${infos.groupId}:${infos.artifactId}:${infos.version}")
              Seq(Module(infos.groupId, infos.artifactId) -> infos.version)
          }
        }

        if (moduleVersions.isEmpty)
          s"No module found for $ghOrg/$ghRepo".left
        else
          moduleVersions.right
    }
  }

}
