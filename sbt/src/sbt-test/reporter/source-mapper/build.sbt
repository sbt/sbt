import sbt.internal.util.ConsoleAppender

extraAppenders := { s => Seq(ConsoleAppender(FakePrintWriter)) }

val assertEmptySourcePositionMappers = taskKey[Unit]("checks that sourcePositionMappers is empty")

val assertAbsolutePathConversion = taskKey[Unit]("checks source mappers convert to absolute path")

val assertVirtualFile = taskKey[Unit]("checks source mappers handle virtual files")

val resetMessages = taskKey[Unit]("empties the messages list")

assertEmptySourcePositionMappers := {
  assert {
    sourcePositionMappers.value.isEmpty
  }
}

assertAbsolutePathConversion := {
  val source = (Compile/sources).value.head
  assert {
    FakePrintWriter.messages.exists(_.contains(s"${source.getAbsolutePath}:3:15: comparing values of types Int and String using `==' will always yield false"))
  }
  assert {
    FakePrintWriter.messages.forall(!_.contains("${BASE}"))
  }
}

assertVirtualFile := {
  val source = (Compile/sources).value.head
  assert {
    FakePrintWriter.messages.exists(_.contains("${BASE}/src/main/scala/Foo.scala:3:15: comparing values of types Int and String using `==' will always yield false"))
  }
  assert {
    FakePrintWriter.messages.forall(!_.contains(source.getAbsolutePath))
  }
}

resetMessages := {
  FakePrintWriter.resetMessages
}
