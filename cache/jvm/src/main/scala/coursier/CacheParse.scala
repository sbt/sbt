package coursier

import java.net.MalformedURLException

import coursier.core.Authentication
import coursier.ivy.IvyRepository
import coursier.util.{Parse, ValidationNel}
import coursier.util.Traverse.TraverseOps

object CacheParse {

  def repository(s: String): Either[String, Repository] =
    if (s == "ivy2local" || s == "ivy2Local")
      Right(Cache.ivy2Local)
    else if (s == "ivy2cache" || s == "ivy2Cache")
      Right(Cache.ivy2Cache)
    else {
      val repo = Parse.repository(s)

      val url = repo.right.map {
        case m: MavenRepository =>
          m.root
        case i: IvyRepository =>
          // FIXME We're not handling metadataPattern here
          i.pattern.chunks.takeWhile {
            case _: coursier.ivy.Pattern.Chunk.Const => true
            case _ => false
          }.map(_.string).mkString
        case r =>
          sys.error(s"Unrecognized repository: $r")
      }

      val validatedUrl = try {
        url.right.map(Cache.url)
      } catch {
        case e: MalformedURLException =>
          Left("Error parsing URL " + url + Option(e.getMessage).fold("")(" (" + _ + ")"))
      }

      validatedUrl.right.flatMap { url =>
        Option(url.getUserInfo) match {
          case None =>
            repo
          case Some(userInfo) =>
            userInfo.split(":", 2) match {
              case Array(user, password) =>
                val baseUrl = new java.net.URL(
                  url.getProtocol,
                  url.getHost,
                  url.getPort,
                  url.getFile
                ).toString

                repo.right.map {
                  case m: MavenRepository =>
                    m.copy(
                      root = baseUrl,
                      authentication = Some(Authentication(user, password))
                    )
                  case i: IvyRepository =>
                    i.copy(
                      pattern = coursier.ivy.Pattern(
                        coursier.ivy.Pattern.Chunk.Const(baseUrl) +: i.pattern.chunks.dropWhile {
                          case _: coursier.ivy.Pattern.Chunk.Const => true
                          case _ => false
                        }
                      ),
                      authentication = Some(Authentication(user, password))
                    )
                  case r =>
                    sys.error(s"Unrecognized repository: $r")
                }

              case _ =>
                Left(s"No password found in user info of URL $url")
            }
        }
      }
    }

  def repositories(l: Seq[String]): ValidationNel[String, Seq[Repository]] =
    l.toVector.validationNelTraverse { s =>
      ValidationNel.fromEither(repository(s))
    }

  def cachePolicies(s: String): ValidationNel[String, Seq[CachePolicy]] =
    s
      .split(',')
      .toVector
      .validationNelTraverse[String, Seq[CachePolicy]] {
        case "offline" =>
          ValidationNel.success(Seq(CachePolicy.LocalOnly))
        case "update-local-changing" =>
          ValidationNel.success(Seq(CachePolicy.LocalUpdateChanging))
        case "update-local" =>
          ValidationNel.success(Seq(CachePolicy.LocalUpdate))
        case "update-changing" =>
          ValidationNel.success(Seq(CachePolicy.UpdateChanging))
        case "update" =>
          ValidationNel.success(Seq(CachePolicy.Update))
        case "missing" =>
          ValidationNel.success(Seq(CachePolicy.FetchMissing))
        case "force" =>
          ValidationNel.success(Seq(CachePolicy.ForceDownload))
        case "default" =>
          ValidationNel.success(Seq(CachePolicy.LocalOnly, CachePolicy.FetchMissing))
        case other =>
          ValidationNel.failure(s"Unrecognized mode: $other")
      }
      .map(_.flatten)

}
