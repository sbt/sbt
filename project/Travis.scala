import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URL, URLConnection}
import java.nio.charset.StandardCharsets

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


  private def readFully(is: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val data = Array.ofDim[Byte](16384)

    var nRead = 0
    while ({
      nRead = is.read(data, 0, data.length)
      nRead != -1
    })
      buffer.write(data, 0, nRead)

    buffer.flush()
    buffer.toByteArray
  }

  private def fetch(url: String, log: Logger): String = {

    val url0 = new URL(url)

    log.info(s"Fetching $url")

    val (rawResp, code) = {

      var conn: URLConnection = null
      var httpConn: HttpURLConnection = null
      var is: InputStream = null

      try {
        conn = url0.openConnection()
        httpConn = conn.asInstanceOf[HttpURLConnection]
        httpConn.setRequestProperty("Accept", "application/vnd.travis-ci.2+json")
        is = conn.getInputStream

        (readFully(is), httpConn.getResponseCode)
      } finally {
        if (is != null)
          is.close()
        if (httpConn != null)
          httpConn.disconnect()
      }
    }

    if (code / 100 != 2)
      sys.error(s"Unexpected response code when getting $url: $code")

    new String(rawResp, StandardCharsets.UTF_8)
  }

  def builds(repo: String, log: Logger): List[Build] = {

    val url = s"https://api.travis-ci.org/repos/$repo/builds"
    val resp = fetch(url, log)

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
    val resp = fetch(url, log)

    resp.decodeEither[Job] match {
      case Left(err) =>
        sys.error(s"Error decoding response from $url: $err")
      case Right(job) =>
        job
    }
  }

}