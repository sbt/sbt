package sbt.librarymanagement

import gigahorse._, support.okhttp.Gigahorse

object Http {
  lazy val http: HttpClient = Gigahorse.http(gigahorse.Config())
}
