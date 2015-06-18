package coursier
package core

import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Promise, Future}
import scalaz.{-\/, \/-, \/, EitherT}
import scalaz.concurrent.Task

import scala.scalajs.js
import js.Dynamic.{global => g}

import scala.scalajs.js.timers._

object Remote {

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  lazy val jsonpAvailable = !js.isUndefined(g.jsonp)

  /** Available if we're running on node, and package xhr2 is installed */
  lazy val xhr = g.require("xhr2")
  def xhrReq() =
    js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]

  def fetchTimeout(target: String, p: Promise[_]) =
    setTimeout(10000) {
      if (!p.isCompleted) {
        p.failure(new Exception(s"Timeout when fetching $target"))
      }
    }

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

  def get(url: String)(implicit executionContext: ExecutionContext): Future[Either[String, Xml.Node]] =
    if (jsonpAvailable)
      proxiedJsonp(url).map(compatibility.xmlParse)
    else {
      val p = Promise[Either[String, Xml.Node]]()
      val xhrReq0 = xhrReq()
      val f = { _: Event =>
        p.success(compatibility.xmlParse(xhrReq0.responseText))
      }
      xhrReq0.onload = f

      xhrReq0.open("GET", url)
      xhrReq0.send()

      fetchTimeout(url, p)
      p.future
    }

}

trait Logger {
  def fetching(url: String): Unit
  def fetched(url: String): Unit
  def other(url: String, msg: String): Unit
}

case class Remote(base: String, logger: Option[Logger] = None) extends Repository {

  def find(module: Module,
           version: String,
           cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    val relPath = {
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        version,
        s"${module.name}-$version.pom"
      )
    } .map(Remote.encodeURIComponent)

    val url = base + relPath.mkString("/")

    EitherT(Task{ implicit ec =>
      logger.foreach(_.fetching(url))
      Remote.get(url).recover{case e: Exception => Left(e.getMessage)}.map{ eitherXml =>
        logger.foreach(_.fetched(url))
        for {
          xml <- \/.fromEither(eitherXml)
          _ = logger.foreach(_.other(url, "is XML"))
          _ <- if (xml.label == "project") \/-(()) else -\/(s"Project definition not found (got '${xml.label}')")
          _ = logger.foreach(_.other(url, "project definition found"))
          proj <- Xml.project(xml)
          _ = logger.foreach(_.other(url, "project definition ok"))
        } yield proj
      }
    })
  }

  def versions(organization: String,
               name: String,
               cachePolicy: CachePolicy): EitherT[Task, String, Versions] = ???

}
