package sbt.librarymanagement

import gigahorse.*, support.apachehttp.Gigahorse
import scala.concurrent.duration.DurationInt

object Http {
  lazy val http: HttpClient = Gigahorse.http(gigahorse.Config().withReadTimeout(60.minutes))
}
