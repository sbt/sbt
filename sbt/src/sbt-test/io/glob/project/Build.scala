import java.nio.file.{ Path, Paths }
import sbt._
import sbt.io.Glob
import sbt.Keys._

object Build {
  val simpleTest = taskKey[Unit]("Check that glob file selectors work")
  val relativeSubdir = Paths.get("subdir")
  val relativeFiles =
    Seq(Paths.get("foo.txt"), Paths.get("bar.json"), relativeSubdir.resolve("baz.yml"))
  val files = taskKey[Path]("The files subdirectory")
  val subdir = taskKey[Path]("The subdir path in the files subdirectory")
  val allFiles = taskKey[Seq[Path]]("Returns all of the regular files in the files subdirectory")
  private def check(actual: Any, expected: Any): Unit =
    if (actual != expected) throw new IllegalStateException(s"$actual did not equal $expected")
  val root = (project in file("."))
    .settings(
      files := (baseDirectory.value / "files").toPath,
      subdir := files.value.resolve("subdir"),
      allFiles := {
        val f = files.value
        relativeFiles.map(f.resolve(_))
      },
      simpleTest := {
        val allPaths: Glob = files.value.allPaths
        val af = allFiles.value.toSet
        val sub = subdir.value
        check(allPaths.all.map(_._1).toSet, af + sub)
        check(allPaths.all.filter(_._2.isRegularFile).map(_._1).toSet, af)
        check(allPaths.all.filter(_._2.isDirectory).map(_._1).toSet, Set(sub))
      }
    )
}