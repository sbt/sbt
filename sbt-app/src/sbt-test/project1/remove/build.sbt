val intsTask = taskKey[Seq[Int]]("A seq of ints task")
val intsSetting = settingKey[Seq[Int]]("A seq of ints setting")
val intsFromScalaV = settingKey[Seq[Int]]("a seq of ints from scalaVersion")
val intsSetSetting = settingKey[Set[Int]]("A set of ints setting")
val stringIntMapSetting = settingKey[Map[String, Int]]("A map of string to int setting")

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

intsSetSetting := Set(1, 2, 3, 4, 5, 6, 7)
intsSetSetting -= 3
intsSetSetting --= Set(1, 2)

stringIntMapSetting := Map("a" -> 1, "b" -> 2 , "c" -> 3, "d" -> 4, "e" -> 5)
stringIntMapSetting -= "c"
stringIntMapSetting --= Seq("a", "b")

val check = taskKey[Unit]("Runs the check")
check := {
  assert(intsTask.value == Seq(4, 5), s"intsTask should be Seq(4, 5) but is ${intsTask.value}")
  assert(intsSetting.value == Seq(4, 5), s"intsSetting should be Seq(4, 5) but is ${intsSetting.value}")
  assert(intsFromScalaV.value == Seq(4, 5), s"intsFromScalaV should be Seq(4, 5) but is ${intsFromScalaV.value}")
  assert(intsSetSetting.value == Set(4, 5, 6, 7), s"intsSetSetting should be Set(4, 5, 6, 7) but is ${intsSetSetting.value}")
  assert(stringIntMapSetting.value == Map("d" -> 4, "e" -> 5), s"stringIntMapSetting should be Map(d -> 4, e -> 5) but is ${stringIntMapSetting.value}")
}
