Compile / excludeFilter := "Bar.scala" || "Baz.scala"

val checkSources = inputKey[Unit]("Check that the compile sources match the input file names")
checkSources := {
  val sources = Def.spaceDelimited("").parsed.toSet
  val actual = (Compile / unmanagedSources).value.map(_.getName).toSet
  assert(sources == actual)
}
