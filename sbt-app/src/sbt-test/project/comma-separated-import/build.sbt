import scala.util, util.Random
import scala.collection.mutable, mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent, concurrent.*

val check = taskKey[Unit]("check comma-separated import works")
check := {
  val _ = Random.nextInt()
  val _ = ArrayBuffer(1, 2, 3)
  val _ = ListBuffer("a", "b")
  val _ = Future.successful(1)
}
