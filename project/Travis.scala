import argonaut._
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._

import sbt.Logger

object Travis {

  final case class BuildId(value: Long) extends AnyVal
  object BuildId {
    implicit val decode: DecodeJson[BuildId] =
      DecodeJson.LongDecodeJson.map(BuildId(_))
  }

  final case class JobId(value: Long) extends AnyVal
  object JobId {
    implicit val decode: DecodeJson[JobId] =
      DecodeJson.LongDecodeJson.map(JobId(_))
  }

  final case class CommitId(value: Long) extends AnyVal
  object CommitId {
    implicit val decode: DecodeJson[CommitId] =
      DecodeJson.LongDecodeJson.map(CommitId(_))
  }

  final case class Build(
    id: BuildId,
    job_ids: List[JobId],
    pull_request: Boolean,
    state: String,
    commit_id: CommitId
  )

  final case class Builds(
    builds: List[Build]
  )

  final case class Commit(
    id: CommitId,
    sha: String,
    branch: String
  )

  final case class JobDetails(
    state: String
  )

  final case class Job(
    commit: Commit,
    job: JobDetails
  )

  val extraHeaders = Seq(
    "Accept" -> "application/vnd.travis-ci.2+json"
  )

  def builds(repo: String, log: Logger): List[Build] = {

    val url = s"https://api.travis-ci.org/repos/$repo/builds"
    val resp = HttpUtil.fetch(url, log, extraHeaders)

    resp.decodeEither[Builds] match {
      case Left(err) =>
        sys.error(s"Error decoding response from $url: $err")
      case Right(builds) =>
        log.info(s"Got ${builds.builds.length} builds")
        builds.builds
    }
  }

  def job(id: JobId, log: Logger): Job = {

    val url = s"https://api.travis-ci.org/jobs/${id.value}"
    val resp = HttpUtil.fetch(url, log, extraHeaders)

    resp.decodeEither[Job] match {
      case Left(err) =>
        sys.error(s"Error decoding response from $url: $err")
      case Right(job) =>
        job
    }
  }

}