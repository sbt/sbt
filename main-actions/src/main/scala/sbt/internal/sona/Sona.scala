/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package sona

import gigahorse.*
import gigahorse.support.apachehttp.Gigahorse
import sbt.internal.sona.SonaClient.failedDeploymentErrorText
import sbt.util.Logger
import sjsonnew.JsonFormat
import sjsonnew.shaded.scalajson.ast.unsafe.JValue
import sjsonnew.support.scalajson.unsafe.{ Converter, Parser, PrettyPrinter }

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import scala.concurrent.*
import scala.concurrent.duration.*

class Sona(client: SonaClient) extends AutoCloseable {
  def uploadBundle(
      bundleZipPath: Path,
      deploymentName: String,
      pt: PublishingType,
      log: Logger,
  ): Unit = {
    val deploymentId = client.uploadBundle(bundleZipPath, deploymentName, pt, log)
    client.waitForDeploy(deploymentId, deploymentName, pt, 1, log)
  }
  def close(): Unit = client.close()
}

class SonaClient(reqTransform: Request => Request, uploadRequestTimeout: FiniteDuration)
    extends AutoCloseable {
  import SonaClient.baseUrl

  private val http = {
    val defaultHttpRequestTimeout = 2.minutes

    val gigahorseConfig = Gigahorse.config
      .withRequestTimeout(defaultHttpRequestTimeout)
      .withReadTimeout(defaultHttpRequestTimeout)

    Gigahorse.http(gigahorseConfig)
  }

  def uploadBundle(
      bundleZipPath: Path,
      deploymentName: String,
      publishingType: PublishingType,
      log: Logger,
  ): String = {
    val maxAttempt = 2
    val waitDurationBetweenAtttempt = 5.seconds
    // Adding an extra 5.seconds as security margins
    val totalAwaitDuration =
      maxAttempt * uploadRequestTimeout + maxAttempt * waitDurationBetweenAtttempt + 5.seconds

    val res = retryF(maxAttempt, waitDurationBetweenAtttempt) { (attempt: Int) =>
      log.info(s"uploading bundle to the Central Portal (attempt: $attempt)")
      // addQuery string doesn't work for post
      val q = queryString(
        "name" -> deploymentName,
        "publishingType" -> (publishingType match {
          case PublishingType.Automatic   => "AUTOMATIC"
          case PublishingType.UserManaged => "USER_MANAGED"
        })
      )
      val req = Gigahorse
        .url(s"${baseUrl}/publisher/upload?$q")
        .post(
          MultipartFormBody(
            FormPart("bundle", bundleZipPath.toFile())
          )
        )
        .withRequestTimeout(uploadRequestTimeout)
      http.run(reqTransform(req), SonaClient.asStringWithErrorBody)
    }
    awaitWithMessage(res, "uploading...", log, totalAwaitDuration)
  }

  private def queryString(kv: (String, String)*): String =
    kv.map { case (k, v) =>
      val encodedV = URLEncoder.encode(v, "UTF-8")
      s"$k=$encodedV"
    }.mkString("&")

  def waitForDeploy(
      deploymentId: String,
      deploymentName: String,
      publishingType: PublishingType,
      attempt: Int,
      log: Logger,
  ): Unit = {
    val status = deploymentStatus(deploymentId)
    log.info(s"deployment $deploymentName ${status.deploymentState} ${attempt}/n")
    val sleepSec =
      if (attempt <= 3) List(5, 5, 10, 15)(attempt)
      else 30
    status.deploymentState match {
      case DeploymentState.FAILED =>
        val errorText = failedDeploymentErrorText(deploymentId, status.errors, log)
        sys.error(errorText)
      case DeploymentState.PENDING | DeploymentState.PUBLISHING | DeploymentState.VALIDATING =>
        Thread.sleep(sleepSec * 1000L)
        waitForDeploy(deploymentId, deploymentName, publishingType, attempt + 1, log)
      case DeploymentState.PUBLISHED if publishingType == PublishingType.Automatic   => ()
      case DeploymentState.VALIDATED if publishingType == PublishingType.UserManaged => ()
      case DeploymentState.VALIDATED =>
        Thread.sleep(sleepSec * 1000L)
        waitForDeploy(deploymentId, deploymentName, publishingType, attempt + 1, log)
      case _ =>
        Thread.sleep(sleepSec * 1000L)
        waitForDeploy(deploymentId, deploymentName, publishingType, attempt + 1, log)
    }
  }

  private def deploymentStatus(deploymentId: String): PublisherStatus = {
    val res = retryF(maxAttempt = 5, waitDurationBetweenAttempt = 5.seconds) { (attempt: Int) =>
      deploymentStatusF(deploymentId)
    }
    Await.result(res, 10.minutes)
  }

  /**
   * https://central.sonatype.org/publish/publish-portal-api/#verify-status-of-the-deployment
   */
  private def deploymentStatusF(deploymentId: String): Future[PublisherStatus] = {
    val req = Gigahorse
      .url(s"${baseUrl}/publisher/status")
      .addQueryString("id" -> deploymentId)
      .post("", StandardCharsets.UTF_8)
    http.run(reqTransform(req), SonaClient.asPublisherStatus)
  }

  /**
   * Retry future function on any error.
   */
  private def retryF[A1](maxAttempt: Int, waitDurationBetweenAttempt: FiniteDuration)(
      f: Int => Future[A1]
  ): Future[A1] = {
    import scala.concurrent.ExecutionContext.Implicits.*
    def impl(retry: Int): Future[A1] = {
      val res = f(retry + 1)
      res.recoverWith {
        case _ if retry < maxAttempt =>
          sleep(waitDurationBetweenAttempt).flatMap(_ => impl(retry + 1))
      }
    }
    impl(0)
  }

  private def awaitWithMessage[A1](
      f: Future[A1],
      msg: String,
      log: Logger,
      awaitDuration: FiniteDuration,
  ): A1 = {
    import scala.concurrent.ExecutionContext.Implicits.*
    def logLoop(attempt: Int): Unit =
      if (!f.isCompleted) {
        if (attempt > 0) {
          log.info(msg)
        }
        sleep(30.second).foreach(_ => logLoop(attempt + 1))
      } else ()
    logLoop(0)
    Await.result(f, awaitDuration)
  }

  def close(): Unit = http.close()

  private def sleep(duration: FiniteDuration)(implicit executor: ExecutionContext): Future[Unit] =
    Future {
      blocking {
        Thread.sleep(duration.toMillis)
      }
    }
}

object Sona {
  def host: String = SonaClient.host
  def oauthClient(userName: String, userToken: String, uploadRequestTimeout: FiniteDuration): Sona =
    new Sona(SonaClient.oauthClient(userName, userToken, uploadRequestTimeout))
}

object SonaClient {
  import sbt.internal.sona.codec.JsonProtocol.given
  val host: String = "central.sonatype.com"
  val baseUrl: String = s"https://$host/api/v1"
  val asJson: FullResponse => JValue = (r: FullResponse) =>
    Parser.parseFromByteBuffer(r.bodyAsByteBuffer).get
  def as[A1: JsonFormat]: FullResponse => A1 = asJson.andThen(Converter.fromJsonUnsafe[A1])
  val asPublisherStatus: FullResponse => PublisherStatus = as[PublisherStatus]

  /**
   * Response handler that returns the body as a String on success (2xx status),
   * or throws a [[SonaStatusError]] with both the status code and response body on failure.
   * This provides more detailed error information than [[gigahorse.StatusError]].
   */
  val asStringWithErrorBody: FullResponse => String = { response =>
    val body = response.bodyAsString
    if (response.status >= 200 && response.status < 300) body
    else throw new SonaStatusError(response.status, body)
  }
  def oauthClient(
      userName: String,
      userToken: String,
      uploadRequestTimeout: FiniteDuration
  ): SonaClient =
    new SonaClient(OAuthClient(userName, userToken), uploadRequestTimeout)

  /**
   * @note non-private visibility only for the tests
   */
  private[sona] def failedDeploymentErrorText(
      deploymentId: String,
      errors: Option[JValue],
      log: Logger
  ): String = {
    val errorsText = errors.map(presentDeploymentValidationErrors(_, log))
    val errorsMessagePart = errorsText match {
      case Some(value) =>
        s" with validation errors:\n$value"
      case None => ""
    }
    s"deployment $deploymentId failed$errorsMessagePart"
  }

  import sbt.internal.sona.SonaClient.PrettyPrint.*

  private def presentDeploymentValidationErrors(errorsNode: JValue, log: Logger): String = {
    PackageDeploymentValidationError.parse(errorsNode) match {
      case Some(errors) =>
        val errorsPresented: Seq[String] = errors.map {
          case PackageDeploymentValidationError(packageDescriptor, packageErrors) =>
            s"""$packageDescriptor
               |${indent(asList(packageErrors), 2)}""".stripMargin
        }
        indent(asList(errorsPresented), 2)
      case None =>
        // Sonatype might change the format of the errors in the future.
        // We shouldn't fail, and as a fallback we pretty print the JSON representation
        log.warn(
          "Sonatype deployment validation errors JSON format has changed. Please update to the latest sbt version or report the issue to the sbt project"
        )
        PrettyPrinter(errorsNode)
    }
  }

  private object PrettyPrint {
    def asList(lines: Seq[String]): String =
      lines.map("- " + _).mkString("\n")

    def indent(text: String, indentSize: Int): String = {
      val indent = " " * indentSize
      text.linesIterator.map(indent + _).mkString("\n")
    }
  }
}

private case class OAuthClient(userName: String, userToken: String)
    extends Function1[Request, Request] {
  val base64Credentials =
    Base64.getEncoder.encodeToString(s"${userName}:${userToken}".getBytes(StandardCharsets.UTF_8))
  def apply(request: Request): Request =
    request.addHeaders("Authorization" -> s"Bearer $base64Credentials")
  override def toString: String = "OAuthClient(****)"
}

sealed trait PublishingType
object PublishingType {
  case object Automatic extends PublishingType
  case object UserManaged extends PublishingType
}

/**
 * Exception thrown when an HTTP request to the Sonatype API fails with a non-2xx status.
 * Unlike [[gigahorse.StatusError]], this exception includes the response body which
 * typically contains useful error details from the server.
 *
 * @param status the HTTP status code
 * @param body the response body content
 */
class SonaStatusError(val status: Int, val body: String)
    extends RuntimeException(
      if (body.nonEmpty) s"Unexpected status: $status\n$body"
      else s"Unexpected status: $status"
    )
