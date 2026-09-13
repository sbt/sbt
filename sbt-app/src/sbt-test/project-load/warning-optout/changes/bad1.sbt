import scala.annotation.nowarn

lazy val check = taskKey[Unit]("")
check := {
  val a = (state.value: @nowarn)
  val b = state.value
  println("hi")
}
