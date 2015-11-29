package coursier

import scalaz.\/
import scalaz.concurrent.Task

sealed trait CachePolicy {
  def apply[T](
    tryRemote: T => Boolean )(
    local: => Task[T] )(
    remote: Option[T] => Task[T]
  ): Task[T]
}

object CachePolicy {
  def saving[E,T](
    remote: => Task[E \/ T] )(
    save: T => Task[Unit]
  ): Task[E \/ T] = {
    for {
      res <- remote
      _ <- res.fold(_ => Task.now(()), t => save(t))
    } yield res
  }

  case object Default extends CachePolicy {
    def apply[T](
      tryRemote: T => Boolean )(
      local: => Task[T] )(
      remote: Option[T] => Task[T]
    ): Task[T] =
      local.flatMap(res => if (tryRemote(res)) remote(Some(res)) else Task.now(res))
  }
  case object LocalOnly extends CachePolicy {
    def apply[T](
      tryRemote: T => Boolean )(
      local: => Task[T] )(
      remote: Option[T] => Task[T]
    ): Task[T] =
      local
  }
  case object ForceDownload extends CachePolicy {
    def apply[T](
      tryRemote: T => Boolean )(
      local: => Task[T] )(
      remote: Option[T] => Task[T]
    ): Task[T] =
      remote(None)
  }
}
