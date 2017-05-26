package sbt.librarymanagement

import gigahorse._, support.okhttp.Gigahorse
import okhttp3.{ OkUrlFactory, OkHttpClient }
import java.net.{ URL, HttpURLConnection }

object Http {
  lazy val http: HttpClient = Gigahorse.http(Gigahorse.config)

  private[sbt] lazy val urlFactory = new OkUrlFactory(http.underlying[OkHttpClient])
  private[sbt] def open(url: URL): HttpURLConnection =
    urlFactory.open(url)
}
