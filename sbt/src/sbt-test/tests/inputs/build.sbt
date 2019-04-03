import java.nio.file.Path

import sbt.Keys._
import sbt.nio.file._
import sbt.nio.Keys._

val allInputs = taskKey[Seq[File]]("")
val allInputsExplicit = taskKey[Seq[File]]("")

val checkInputs = inputKey[Unit]("")
val checkInputsExplicit = inputKey[Unit]("")

allInputs := (Compile / unmanagedSources / fileInputs).value.all(fileTreeView.value).map(_._1.toFile)

checkInputs := {
  val res = allInputs.value
  val scala = (Compile / scalaSource).value
  val expected = Def.spaceDelimited("<args>").parsed.map(scala / _).toSet
  assert(res.toSet == expected)
}

// In this test we override the FileTree.Repository used by the all method.
allInputsExplicit := {
  val files = scala.collection.mutable.Set.empty[File]
  val underlying = fileTreeView.value
  val view = new FileTreeView[(Path, FileAttributes)] {
    override def list(path: Path): Seq[(Path, FileAttributes)] = {
      val res = underlying.list(path)
      files ++= res.map(_._1.toFile)
      res
    }
  }
  val include = (Compile / unmanagedSources / includeFilter).value
  val _ = (Compile / unmanagedSources / fileInputs).value.all(view).map(_._1.toFile).toSet
  files.filter(include.accept).toSeq
}

checkInputsExplicit := {
  val res = allInputsExplicit.value
  val scala = (Compile / scalaSource).value
  val expected = Def.spaceDelimited("<args>").parsed.map(scala / _).toSet
  assert(res.toSet == expected)
}
