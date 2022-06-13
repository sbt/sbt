package sbt.librarymanagement

import gigahorse._, support.apachehttp.Gigahorse
import scala.concurrent.duration.DurationInt

object Http {
  lazy val http: HttpClient = Gigahorse.http(gigahorse.Config().withReadTimeout(60.minutes))
}
