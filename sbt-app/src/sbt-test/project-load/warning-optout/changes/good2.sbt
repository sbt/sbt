import scala.annotation.nowarn

lazy val check = taskKey[Unit]("")
check := {
  val s = (state.value: @nowarn("msg=transient key"))
  println("hi")
}
