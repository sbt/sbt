package coursier.test

package object compatibility {

  implicit val executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

}
