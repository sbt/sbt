val foo = inputKey[Unit]("broken task")
foo := { throw new IllegalStateException("foo") }

val exists = inputKey[Unit]("check that the file was written")
exists := {
  val filename = Def.spaceDelimited("").parsed.head
  assert((baseDirectory.value / filename).exists)
}

Global / onChangedBuildSource := ReloadOnSourceChanges

val sub = project
