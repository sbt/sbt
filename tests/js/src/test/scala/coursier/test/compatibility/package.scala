package coursier.test

import scala.concurrent.{Promise, ExecutionContext, Future}
import scala.scalajs.js
import js.Dynamic.{global => g}

package object compatibility {

  implicit val executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  lazy val fs = g.require("fs")

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] = {
    val p = Promise[String]()

    fs.readFile("tests/shared/src/test/resources/" + path, "utf-8", {
      (err: js.Dynamic, data: js.Dynamic) =>
        if (js.isUndefined(err) || err == null) p.success(data.asInstanceOf[String])
        else p.failure(new Exception(err.toString))
        ()
    }: js.Function2[js.Dynamic, js.Dynamic, Unit])

    p.future
  }

}
