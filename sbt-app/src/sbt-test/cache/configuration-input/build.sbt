import sjsonnew.BasicJsonProtocol.*

val foo = taskKey[String]("")
val checkCompile = taskKey[Unit]("")
val checkTest = taskKey[Unit]("")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val fooSettings = Seq(
  foo := configuration.value.name,
)

inConfig(Compile)(fooSettings)
inConfig(Test)(fooSettings)

checkCompile := Def.uncached {
  val actual = (Compile / foo).value
  assert(actual == "compile", s"Compile / foo = $actual")
}

checkTest := Def.uncached {
  val actual = (Test / foo).value
  assert(actual == "test", s"Test / foo = $actual")
}
