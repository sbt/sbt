val intsTask = taskKey[Seq[Int]]("A seq of ints task")
val intsSetting = settingKey[Seq[Int]]("A seq of ints setting")
val intsFromScalaV = settingKey[Seq[Int]]("a seq of ints from scalaVersion")

scalaVersion := "2.11.6"

intsTask := Seq(1, 2, 3, 4, 5, 6, 7)
intsTask -= 3
intsTask --= Seq(1, 2)
intsTask -= Option(6)
intsTask --= Option(7)

intsSetting := Seq(1, 2, 3, 4, 5, 6, 7)
intsSetting -= 3
intsSetting --= Seq(1, 2)
intsSetting -= Option(6)
intsSetting --= Option(7)

intsFromScalaV := Seq(1, 2, 3, 4, 5, 6, 7)
intsFromScalaV -= { if (scalaVersion.value == "2.11.6") 3 else 5 }
intsFromScalaV --= { if (scalaVersion.value == "2.11.6") Seq(1, 2) else Seq(4) }
intsFromScalaV -= { if (scalaVersion.value == "2.11.6") Option(6) else None }
intsFromScalaV --= { if (scalaVersion.value == "2.11.6") Option(7) else None }

val check = taskKey[Unit]("Runs the check")
check := {
  assert(intsTask.value == Seq(4, 5), s"intsTask should be Seq(4, 5) but is ${intsTask.value}")
  assert(intsSetting.value == Seq(4, 5), s"intsSetting should be Seq(4, 5) but is ${intsSetting.value}")
  assert(intsFromScalaV.value == Seq(4, 5), s"intsFromScalaV should be Seq(4, 5) but is ${intsFromScalaV.value}")
}
