import java.nio.file.{ Files, Paths }
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

val createSymlinks = taskKey[Unit]("create symlinks to source files and directories")
createSymlinks := {
  val base = baseDirectory.value.toPath
  val srcDir = base / "src" / "main" / "scala"
  val foo = Files.createDirectories(srcDir / "file-source") / "Foo.scala"
  Files.createSymbolicLink(srcDir / "sources", Files.createDirectories(base / "sources"))
  Files.deleteIfExists(foo)
  Files.createSymbolicLink(foo, base / "file-source" / "Foo.scala")
}

ThisBuild / watchOnFileInputEvent := {
  val srcDir = baseDirectory.value.toPath / "src" / "main" / "scala"
  (_: Int, event: Watch.Event) =>
    event.path match {
      case p if p == (srcDir / "file-source" / "Foo.scala") => Watch.CancelWatch
      case p if p == (srcDir / "sources" / "Bar.scala")     => Watch.CancelWatch
      case _                                                => Watch.Ignore
    }
}

val copySource = inputKey[Unit]("copy a source file from changes")
copySource := {
  val relative = Def.spaceDelimited("").parsed.head.split("/") match {
    case Array(head)            => Paths.get(head)
    case Array(head, tail @ _*) => tail.foldLeft(Paths.get(head))(_ / _)
  }
  val base = baseDirectory.value.toPath
  Files.copy((base / "changes").resolve(relative), base.resolve(relative), REPLACE_EXISTING)
}

val removeLink = inputKey[Unit]("remove a symlink")
removeLink := {
  val relative = Def.spaceDelimited("").parsed.head.split("/") match {
    case Array(head)            => Paths.get(head)
    case Array(head, tail @ _*) => tail.foldLeft(Paths.get(head))(_ / _)
  }
  val srcDir = baseDirectory.value.toPath / "src" / "main" / "scala"
  Files.deleteIfExists(srcDir.resolve(relative))
}

commands += Command.single("skipWindows") { (state, command) =>
  if (scala.util.Properties.isWin) state else command :: state
}
