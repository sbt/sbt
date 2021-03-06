import sbt.internal.util.ConsoleAppender
import sbt.internal.inc.StringVirtualFile
import xsbti.compile.CompileOptions

lazy val assertEmptySourcePositionMappers = taskKey[Unit]("checks that sourcePositionMappers is empty")
lazy val assertAbsolutePathConversion = taskKey[Unit]("checks source mappers convert to absolute path")
lazy val assertVirtualFile = taskKey[Unit]("checks source mappers handle virtual files")
lazy val resetMessages = taskKey[Unit]("empties the messages list")

lazy val root = (project in file("."))
  .settings(
    extraAppenders := { s => Seq(ConsoleAppender(FakePrintWriter)) },
    Compile / compile / compileOptions ~= { old: CompileOptions =>
      old.withSources(StringVirtualFile("<>::A.scala",
        """object X""") +: old.sources) },
    assertEmptySourcePositionMappers := {
      assert {
        sourcePositionMappers.value.isEmpty
      }
    },
    assertAbsolutePathConversion := {
      val source = (Compile/sources).value.head
      assert {
        FakePrintWriter.messages.exists(_.contains(s"${source.getAbsolutePath}:3:15: comparing values of types Int and String using `==` will always yield false"))
      }
      assert {
        FakePrintWriter.messages.forall(!_.contains("${BASE}"))
      }
    },
    assertVirtualFile := {
      val source = (Compile/sources).value.head
      assert {
        FakePrintWriter.messages.exists(_.contains("${BASE}/src/main/scala/Foo.scala:3:15: comparing values of types Int and String using `==` will always yield false"))
      }
      assert {
        FakePrintWriter.messages.forall(!_.contains(source.getAbsolutePath))
      }
    },
    resetMessages := {
      FakePrintWriter.resetMessages
    }
  )
