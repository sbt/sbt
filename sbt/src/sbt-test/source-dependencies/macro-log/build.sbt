dependsOn(macros)

scalaVersion in Global := "2.11.8"

val macros = project settings (libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value)

def expectedStringFor(l: String) =
  s"Because $l contains a macro definition, the following dependencies are invalidated unconditionally"

TaskKey[Unit]("checkPresent1") := check(expectedStringFor("p.Foo$"), expectPresent = true).value
TaskKey[Unit]("checkMissing2") := check(expectedStringFor("p.Bar$"), expectPresent = false).value
TaskKey[Unit]("checkPresent3") := check(expectedStringFor("p.Baz$"), expectPresent = true).value

def check(expectedString: String, expectPresent: Boolean) = Def task {
  val last = IO read (BuiltinCommands lastLogFile state.value).get
  val present = last contains expectedString
  if (expectPresent && !present) sys error "Expected there to be a log message for macro recompilation"
  if (!expectPresent && present) sys error "Expected there to NOT be a log message for macro recompilation"
}
