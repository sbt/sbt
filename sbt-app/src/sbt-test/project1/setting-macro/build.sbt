  import complete.DefaultParsers._

name := {
   // verify lazy vals are handled (#952)
	lazy val x = "sdf"
   x
}

lazy val demo = inputKey[String]("sample")

def parser: complete.Parser[(Int,String)] = token(Space ~> IntBasic <~ Space) ~ token("red")

demo := {
  // verify pattern match on the lhs is handled (#994)
  val (n, s) = parser.parsed
  s * n
}

// Tests for correct Symbol owner structure in the lifted qualifiers of
// the `.value` macro within a task macro. (#1150)
val key1 = taskKey[Unit]("")

key1 := {
  val foo = (sourceDirectory in Compile).apply(base => base).value.get
	testFrameworks.value.flatMap(f =>
		None.map(_ => f)
	)
  ()
}

// https://github.com/sbt/sbt/issues/1107
def appcfgTask(a: String, b: String) = Def.task("")

TaskKey[Unit]("test") := appcfgTask(b = "", a = "").value
