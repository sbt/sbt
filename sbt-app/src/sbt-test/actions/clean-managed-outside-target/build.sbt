name := "clean-managed-outside-target"
scalaVersion := "3.3.1"
Compile / sourceManaged := baseDirectory.value / "src_managed"
Compile / sourceGenerators += Def.task {
  val file = (Compile / sourceManaged).value / "demo" / "Test.scala"
  IO.write(file, """object Test extends App { println("Hi") }""")
  Seq(file)
}.taskValue
