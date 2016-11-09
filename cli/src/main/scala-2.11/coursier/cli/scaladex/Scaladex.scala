package coursier.cli.scaladex

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

import argonaut._
import Argonaut._
import ArgonautShapeless._
import coursier.Module

import scalaz.{-\/, \/, \/-}
import scalaz.Scalaz.ToEitherOps
import scalaz.Scalaz.ToEitherOpsFromEither

object Scaladex {

  // quick & dirty API for querying scaladex

  case class SearchResult(
    /** GitHub organization */
    organization: String,
    /** GitHub repository */
    repository: String,
    /** Scaladex artifact names */
    artifacts: List[String] = Nil
  )

  def search(name: String, target: String, scalaVersion: String): String \/ Seq[SearchResult] = {

    val url = new java.net.URL(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/search?q=$name&target=$target&scalaVersion=$scalaVersion"
    )

    var conn: HttpURLConnection = null

    val b = try {
      conn = url.openConnection().asInstanceOf[HttpURLConnection]
      // FIXME See below
      // conn.setRequestProperty("Accept", "application/json")

      coursier.Platform.readFullySync(conn.getInputStream)
    } finally {
      if (conn != null)
        conn.disconnect()
    }

    val s = new String(b, StandardCharsets.UTF_8)

    s.decodeEither[List[SearchResult]].disjunction
  }

  case class ArtifactInfos(
    /** Dependency group ID (aka organization) */
    groupId: String,
    /** Dependency artifact ID (aka name or module name) */
    artifactId: String,
    /** Dependency version */
    version: String
  )

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @param artifactName: Scaladex artifact name
    * @return
    */
  def artifactInfos(organization: String, repository: String, artifactName: String): String \/ ArtifactInfos = {

    val url = new java.net.URL(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/project?organization=$organization&repository=$repository&artifact=$artifactName"
    )

    var conn: HttpURLConnection = null

    val b = try {
      conn = url.openConnection().asInstanceOf[HttpURLConnection]
      // FIXME See below
      // conn.setRequestProperty("Accept", "application/json")

      coursier.Platform.readFullySync(conn.getInputStream)
    } finally {
      if (conn != null)
        conn.disconnect()
    }

    val s = new String(b, StandardCharsets.UTF_8)

    s.decodeEither[ArtifactInfos].disjunction
  }

  /**
    *
    * @param organization: GitHub organization
    * @param repository: GitHub repository name
    * @return
    */
  def artifactNames(organization: String, repository: String): String \/ Seq[String] = {

    val url = new java.net.URL(
      // FIXME Escaping
      s"https://index.scala-lang.org/api/scastie/project?organization=$organization&repository=$repository"
    )

    var conn: HttpURLConnection = null

    val b = try {
      conn = url.openConnection().asInstanceOf[HttpURLConnection]
      // FIXME report to scaladex, it should accept that (it currently returns JSON as text/plain)
      // conn.setRequestProperty("Accept", "application/json")

      coursier.Platform.readFullySync(conn.getInputStream)
    } finally {
      if (conn != null)
        conn.disconnect()
    }

    val s = new String(b, StandardCharsets.UTF_8)

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
