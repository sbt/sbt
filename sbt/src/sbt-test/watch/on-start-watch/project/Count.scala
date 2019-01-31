import sbt._
import scala.util.Try

object Count {
  private var count = 0
  def get: Int = count
  def increment(): Unit = {
    count += 1
  }
  def reset(): Unit = {
    count = 0
  }
  def reloadCount(file: File): Int = Try(IO.read(file).toInt).getOrElse(0)
}
