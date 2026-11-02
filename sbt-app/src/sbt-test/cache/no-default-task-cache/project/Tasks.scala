import sbt.*
import sbt.Keys.*

object Tasks:
  val count1 = taskKey[Unit]("")
  val count2 = taskKey[Unit]("")
  val check = inputKey[Unit]("")

  def bump(f: File): Unit = IO.append(f, "x\n")

  val scalaSettings = Seq(
    count2 := bump(target.value / "count2.txt")
  )
