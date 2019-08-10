
val checkSources = inputKey[Unit]("Check that the compile sources match the input file names")
checkSources := {
  val sources = Def.spaceDelimited("").parsed.toSet
  val actual = (Compile / unmanagedSources).value.map(_.getName).toSet
  assert(sources == actual)
}

val oldExcludeFilter = settingKey[sbt.io.FileFilter]("the default exclude filter")
oldExcludeFilter :=  "Bar.scala" || "Baz.scala"

Compile / excludeFilter := oldExcludeFilter.value

val newFilter = settingKey[sbt.nio.file.PathFilter]("an alternative path filter")
newFilter := !sbt.nio.file.PathFilter(** / "{Baz,Bar}.scala")