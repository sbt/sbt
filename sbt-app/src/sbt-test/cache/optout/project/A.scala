import sbt.*
import sbt.util.cacheOptOut

case class A()

object CustomKeys:
  @cacheOptOut()
  val aa = taskKey[A]("")
  val map1 = taskKey[String]("")
  val mapN1 = taskKey[Unit]("")
