package coursier

import coursier.util.{EitherT, Task}
import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.scalajs.js.timers._

object Platform {

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  lazy val jsonpAvailable = !js.isUndefined(g.jsonp)

  val timeout = 
    if (jsonpAvailable)
      10000 // Browser - better to have it > 5000 for complex resolutions
    else
      4000  // Node - tests crash if not < 5000

  /** Available if we're running on node, and package xhr2 is installed */
  lazy val xhr = g.require("xhr2")
  def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(timeout) {
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

  val artifact: Fetch.Content[Task] = { artifact =>
    EitherT(
      Task { implicit ec =>
        get(artifact.url)
          .map(Right(_))
          .recover { case e: Exception =>
          Left(e.toString + Option(e.getMessage).fold("")(" (" + _ + ")"))
        }
      }
    )
  }

  def fetch(
    repositories: Seq[core.Repository]
  ): Fetch.Metadata[Task] =
    Fetch.from(repositories, Platform.artifact)

  trait Logger {
    def fetching(url: String): Unit
    def fetched(url: String): Unit
    def other(url: String, msg: String): Unit
  }

  def artifactWithLogger(logger: Logger): Fetch.Content[Task] = { artifact =>
    EitherT(
      Task { implicit ec =>
        Future(logger.fetching(artifact.url))
          .flatMap(_ => get(artifact.url))
          .map { s => logger.fetched(artifact.url); Right(s) }
          .recover { case e: Exception =>
            val msg = e.toString + Option(e.getMessage).fold("")(" (" + _ + ")")
            logger.other(artifact.url, msg)
            Left(msg)
          }
      }
    )
  }

}
