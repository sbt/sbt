package coursier

import java.net.MalformedURLException

import coursier.ivy.IvyRepository
import coursier.util.Parse

import scalaz._, Scalaz._

object CacheParse {

  def repository(s: String): Validation[String, Repository] =
    if (s == "ivy2local" || s == "ivy2Local")
      Cache.ivy2Local.success
    else if (s == "ivy2cache" || s == "ivy2Cache")
      Cache.ivy2Cache.success
    else {
      val repo = Parse.repository(s)

      val url = repo match {
        case m: MavenRepository =>
          m.root
        case i: IvyRepository =>
          i.pattern
        case r =>
          sys.error(s"Unrecognized repository: $r")
      }

      try {
        Cache.url(url)
        repo.success
      } catch {
        case e: MalformedURLException =>
          ("Error parsing URL " + url + Option(e.getMessage).map(" (" + _ + ")").mkString).failure
      }
    }

  def repositories(l: Seq[String]): ValidationNel[String, Seq[Repository]] =
    l.toVector.traverseU { s =>
      repository(s).leftMap(_.wrapNel)
    }

  def cachePolicies(s: String): ValidationNel[String, Seq[CachePolicy]] =
    s.split(',').toVector.traverseU {
      case "offline" =>
        Seq(CachePolicy.LocalOnly).successNel
      case "update-local-changing" =>
        Seq(CachePolicy.LocalUpdateChanging).successNel
      case "update-local" =>
        Seq(CachePolicy.LocalUpdate).successNel
      case "update-changing" =>
        Seq(CachePolicy.UpdateChanging).successNel
      case "update" =>
        Seq(CachePolicy.Update).successNel
      case "missing" =>
        Seq(CachePolicy.FetchMissing).successNel
      case "force" =>
        Seq(CachePolicy.ForceDownload).successNel
      case "default" =>
        Seq(CachePolicy.LocalOnly, CachePolicy.FetchMissing).successNel
      case other =>
        s"Unrecognized mode: $other".failureNel
    }.map(_.flatten)

}
