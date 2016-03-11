val intsTask = taskKey[Seq[Int]]("A seq of ints task")
val intsSetting = settingKey[Seq[Int]]("A seq of ints setting")
val intsFromScalaV = settingKey[Seq[Int]]("a seq of ints from scalaVersion")

scalaVersion := "2.11.6"

intsTask := Nil
intsTask += 3
intsTask ++= Seq(1, 2)
intsTask += Option(6)
intsTask ++= Option(7)

intsSetting := Nil
intsSetting += 3
intsSetting ++= Seq(1, 2)
intsSetting += Option(6)
intsSetting ++= Option(7)

intsFromScalaV := Nil
intsFromScalaV += { if (scalaVersion.value == "2.11.6") 3 else 5 }
intsFromScalaV ++= { if (scalaVersion.value == "2.11.6") Seq(1, 2) else Seq(4) }
intsFromScalaV += { if (scalaVersion.value == "2.11.6") Option(6) else None }
intsFromScalaV ++= { if (scalaVersion.value == "2.11.6") Option(7) else None }

val check = taskKey[Unit]("Runs the check")
check := {
  assertEquals("intsTask", intsTask.value, Seq(3, 1, 2, 6, 7))
  assertEquals("intsSetting", intsSetting.value, Seq(3, 1, 2, 6, 7))
  assertEquals("intsFromScalaV", intsFromScalaV.value, Seq(3, 1, 2, 6, 7))
}

def assertEquals[T](label: String, actual: T, expect: T) =
  assert(actual == expect, s"$label should be $expect but is $actual")
