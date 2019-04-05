package sbt.internal

import sbt.internal.librarymanagement.{ CustomHttp => LMCustomHttp }
import okhttp3._

import sbt.BuildSyntax._
import sbt.KeyRanks._

object CustomHttp {
  val okhttpClientBuilder =
    settingKey[OkHttpClient.Builder]("Builder for the HTTP client.").withRank(CSetting)
  val okhttpClient =
    settingKey[OkHttpClient]("HTTP client used for library management.").withRank(CSetting)

  def defaultHttpClientBuilder: OkHttpClient.Builder = {
    LMCustomHttp.defaultHttpClientBuilder
  }
}
