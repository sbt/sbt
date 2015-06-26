package coursier
package core

import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Promise, Future}
import scalaz.{-\/, \/-, EitherT}
import scalaz.concurrent.Task

import scala.scalajs.js
import js.Dynamic.{global => g}

import scala.scalajs.js.timers._

object Fetch {

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  lazy val jsonpAvailable = !js.isUndefined(g.jsonp)

  /** Available if we're running on node, and package xhr2 is installed */
  lazy val xhr = g.require("xhr2")
  def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(5000) {
      if (!p.isCompleted) {
        p.failure(new Exception(s"Timeout when fetching $target"))
      }
    }

  // FIXME Take into account HTTP error codes from YQL response
  def proxiedJsonp(url: String)(implicit executionContext: ExecutionContext): Future[String] = {
    val url0 =
      "https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20xml%20where%20url%3D%22" +
        encodeURIComponent(url) +
        "%22&format=jsonp&diagnostics=true"

    val p = Promise[String]()

    g.jsonp(url0, (res: js.Dynamic) => if (!p.isCompleted) {
      val success = !js.isUndefined(res) && !js.isUndefined(res.results)
      if (success)
        p.success(res.results.asInstanceOf[js.Array[String]].mkString("\n"))
      else
        p.failure(new Exception(s"Fetching $url ($url0)"))
    })

    fetchTimeout(s"$url ($url0)", p)
    p.future
  }

  def get(url: String)(implicit executionContext: ExecutionContext): Future[String] =
    if (jsonpAvailable)
      proxiedJsonp(url)
    else {
      val p = Promise[String]()
      val xhrReq0 = xhrReq()
      val f = { _: Event =>
        p.success(xhrReq0.responseText)
      }
      xhrReq0.onload = f

      xhrReq0.open("GET", url)
      xhrReq0.send()

      fetchTimeout(url, p)
      p.future
    }

  trait Logger {
    def fetching(url: String): Unit
    def fetched(url: String): Unit
    def other(url: String, msg: String): Unit
  }

}

case class Fetch(root: String,
                 logger: Option[Fetch.Logger] = None) {

  def apply(artifact: Artifact,
            cachePolicy: Repository.CachePolicy): EitherT[Task, String, String] = {

    val url0 = root + artifact.url

    EitherT(
      Task { implicit ec =>
        Future(logger.foreach(_.fetching(url0)))
          .flatMap(_ => Fetch.get(url0))
          .map{ s => logger.foreach(_.fetched(url0)); \/-(s) }
          .recover{case e: Exception =>
            logger.foreach(_.other(url0, e.getMessage))
            -\/(e.getMessage)
          }
      }
    )
  }

}
