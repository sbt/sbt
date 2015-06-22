package coursier.core

import scalaz.{-\/, \/-, \/, EitherT}
import scalaz.concurrent.Task

trait Repository {
  def find(module: Module, version: String, cachePolicy: CachePolicy = CachePolicy.Default): EitherT[Task, String, Project]
  def versions(organization: String, name: String, cachePolicy: CachePolicy = CachePolicy.Default): EitherT[Task, String, Versions]
}

sealed trait CachePolicy {
  def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T]

  def saving[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T])(save: => T => Task[Unit]): Task[E \/ T] =
    apply(local)(CachePolicy.saving(remote)(save))
}

object CachePolicy {
  def saving[E,T](remote: => Task[E \/ T])(save: T => Task[Unit]): Task[E \/ T] = {
    for {
      res <- remote
      _ <- res.fold(_ => Task.now(()), t => save(t))
    } yield res
  }

  case object Default extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      local.flatMap(res => if (res.isLeft) remote else Task.now(res))
  }
  case object LocalOnly extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      local
  }
  case object ForceDownload extends CachePolicy {
    def apply[E,T](local: => Task[E \/ T])(remote: => Task[E \/ T]): Task[E \/ T] =
      remote
  }
}

trait MavenRepository extends Repository {

  def find(module: Module,
           version: String,
           cachePolicy: CachePolicy): EitherT[Task, String, Project] = {

    Parse.versionInterval(version).filter(_.isValid) match {
      case None => findNoInterval(module, version, cachePolicy)
      case Some(itv) =>
        versions(module.organization, module.name, cachePolicy).flatMap { versions0 =>
          val eitherVersion = {
            val release = Version(versions0.release)
            if (itv.contains(release)) \/-(versions0.release)
            else {
              val inInterval = versions0.available.map(Version(_)).filter(itv.contains)
              if (inInterval.isEmpty) -\/(s"No version found for $version")
              else \/-(inInterval.max.repr)
            }
          }

          eitherVersion match {
            case -\/(reason) => EitherT[Task, String, Project](Task.now(-\/(reason)))
            case \/-(version0) => findNoInterval(module, version0, cachePolicy)
              .map(_.copy(versions = Some(versions0)))
          }
        }
    }
  }

  def findNoInterval(module: Module,
                     version: String,
                     cachePolicy: CachePolicy): EitherT[Task, String, Project]

}
