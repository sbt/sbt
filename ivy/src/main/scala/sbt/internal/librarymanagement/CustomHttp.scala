package sbt.internal.librarymanagement

import gigahorse.HttpClient
import okhttp3.{ JavaNetAuthenticator => _, _ }
import sbt.librarymanagement.Http

object CustomHttp {
  private[this] def http0: HttpClient = Http.http

  private[sbt] def defaultHttpClientBuilder: OkHttpClient.Builder = {
    http0
      .underlying[OkHttpClient]
      .newBuilder()
      .authenticator(new sbt.internal.librarymanagement.JavaNetAuthenticator)
      .followRedirects(true)
      .followSslRedirects(true)
  }

  private[sbt] lazy val defaultHttpClient: OkHttpClient =
    defaultHttpClientBuilder.build
}
