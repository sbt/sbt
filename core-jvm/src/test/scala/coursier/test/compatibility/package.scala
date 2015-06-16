package coursier.test

import scala.concurrent.Future
import scalaz.concurrent.Task

package object compatibility {

  implicit val executionContext = scala.concurrent.ExecutionContext.global

  implicit class TaskExtensions[T](val underlying: Task[T]) extends AnyVal {
    def runF: Future[T] = Future.successful(underlying.run)
  }

}
