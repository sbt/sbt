lazy val a1 = settingKey[Boolean]("")

scalaVersion := "3.8.4"
a1 := true

Compile / sourceGenerators += {
  val _ = a1.value
  Def.task {
    Seq.empty[File]
  }.taskValue
}
