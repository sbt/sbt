import argonaut._
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._

import sbt.Logger

object Appveyor {

  final case class Build(
    buildId: Long,
    branch: String,
    commitId: String,
    status: String
  )

  def branchLastBuild(repo: String, branch: String, log: Logger): Build = {

    final case class Response(build: Build)

    val url = s"https://ci.appveyor.com/api/projects/$repo/branch/$branch"
    val rawResp = HttpUtil.fetch(url, log)

    rawResp.decodeEither[Response] match {
      case Left(err) =>
        sys.error(s"Error decoding response from $url: $err")
      case Right(resp) =>
        resp.build
    }
  }

}
