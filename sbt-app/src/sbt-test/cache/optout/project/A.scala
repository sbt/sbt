import sbt.*
import sbt.util.cacheLevel

case class A()

object CustomKeys:
  @transient
  val aa = taskKey[A]("")
  val map1 = taskKey[String]("")
  val mapN1 = taskKey[Unit]("")
