package coursier
package core

import org.scalajs.dom.raw.{Event, XMLHttpRequest}

import scala.concurrent.{ExecutionContext, Promise, Future}
import scalaz.{-\/, \/-, \/, EitherT}
import scalaz.concurrent.Task

import scala.scalajs.js
import js.Dynamic.{global => g}

object Remote {

  def encodeURIComponent(s: String): String =
    g.encodeURIComponent(s).asInstanceOf[String]

  lazy val jsonpAvailable = js.isUndefined(g.jsonp)

  def proxiedJsonp(url: String)(implicit executionContext: ExecutionContext): Future[String] =
    if (url.contains("{")) Future.successful("") // jsonp callback never gets executed in this case (empty response)
    else {

    // FIXME url is put between quotes in the YQL query, which could sometimes require some extra encoding
    val url0 =
      "https://query.yahooapis.com/v1/public/yql?q=select%20*%20from%20xml%20where%20url%3D%22" +
        encodeURIComponent(url) +
        "%22&format=jsonp&diagnostics=true"

    val p = Promise[String]()

    // FIXME Promise may not be always completed in case of failure

    g.jsonp(url0, (res: js.Dynamic) => {
      val success = !js.isUndefined(res) && !js.isUndefined(res.results)
      if (success)
        p.success(res.results.asInstanceOf[js.Array[String]].mkString("\n"))
      else
        p.failure(new Exception(s"Fetching $url ($url0)"))
    })

    p.future
  }

  def get(url: String)(implicit executionContext: ExecutionContext): Future[Either[String, Xml.Node]] =
    if (jsonpAvailable) {
      // Assume we're running on node, and that node package xhr2 is installed
      val xhr = g.require("xhr2")

      val p = Promise[Either[String, Xml.Node]]()
      val req = js.Dynamic.newInstance(xhr)().asInstanceOf[XMLHttpRequest]
      val f = { _: Event =>
        p.success(compatibility.xmlParse(req.responseText))
      }
      req.onload = f

      req.open("GET", url)
      req.send()

      p.future
    } else {
      proxiedJsonp(url).map(compatibility.xmlParse)
    }

}

trait Logger {
  def fetching(url: String): Unit
  def fetched(url: String): Unit
  def other(url: String, msg: String): Unit
}

case class Remote(base: String, logger: Option[Logger] = None) extends Repository {

  def find(module: Module,
           cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    val relPath =
      module.organization.split('.').toSeq ++ Seq(
        module.name,
        module.version,
        s"${module.name}-${module.version}.pom"
      )

    val url = base + relPath.mkString("/")

    EitherT(Task{ implicit ec =>
      logger.foreach(_.fetching(url))
      Remote.get(url).map{ eitherXml =>
        logger.foreach(_.fetched(url))
        for {
          xml <- \/.fromEither(eitherXml)
          _ = logger.foreach(_.other(url, "is XML"))
          _ <- if (xml.label == "project") \/-(()) else -\/("Project definition not found")
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
