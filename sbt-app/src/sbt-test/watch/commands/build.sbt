import java.nio.file.Files

import scala.collection.JavaConverters._

val foo = taskKey[Unit]("foo")
foo := {
  val fooTxt = baseDirectory.value / "foo.txt"
  val _ = println(s"foo inputs: ${foo.inputFiles}")
  IO.write(fooTxt, "foo")
  println(s"foo wrote to $foo")
}
foo / fileInputs += baseDirectory.value.toGlob / "foo.txt"

Global / watchTriggers += baseDirectory.value.toGlob / "bar.txt"

commands ++= Seq(
  Command.command("eval-foo") { s =>
    Project.extract(s).runTask(foo, s)
    s
  },
  Command.command("write-bar") { s =>
    val bar = Project.extract(s).get(baseDirectory) / "bar.txt"
    IO.write(bar, "bar")
    println(s"write-bar wrote to $bar")
    s
  }
)

watchOnFileInputEvent := { (_, _) => sbt.nio.Watch.CancelWatch }

/*
 * This test ensures that when watching a cross build that commands are run for multiple scala
 * versions. It also checks that the fileInputs are automatically detected during task evaluation
 * even though we can't directly inspect the fileInputs of the cross ('+') command.
 */
val expectFailure = taskKey[Unit]("expect failure")
expectFailure := {
  val main = baseDirectory.value.toPath / "src" / "main"
  val crossDir = main / (if (scalaVersion.value.startsWith("2.11")) "scala-2.11" else "scala-2.12")
  (Compile / compile).result.value.toEither match {
    case Left(_) =>
      Files.write(crossDir / "Foo.scala", "class Foo".getBytes)
      throw new IllegalStateException("Compilation failed.")
    case Right(_) =>
      if (!Files.walk(main).iterator.asScala.exists(_.getFileName.toString == "first.scala"))
        Files.write(crossDir / "first.scala", "class first".getBytes)
      else Files.write(crossDir / "second.scala", "class second".getBytes)
  }
}
expectFailure / watchOnFileInputEvent := { (_, e) =>
  if (e.path.getFileName.toString == "second.scala") sbt.nio.Watch.CancelWatch
  else sbt.nio.Watch.Trigger
}


crossScalaVersions := Seq("2.11.12", "2.12.20")
