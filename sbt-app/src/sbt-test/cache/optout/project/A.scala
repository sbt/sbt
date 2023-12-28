import sbt.*
import sbt.util.cacheLevel

case class A()

object CustomKeys:
  @cacheLevel(include = Array.empty)
  val aa = taskKey[A]("")
  val map1 = taskKey[String]("")
  val mapN1 = taskKey[Unit]("")
