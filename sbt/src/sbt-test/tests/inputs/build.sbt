import java.nio.file.Path

import sbt.internal.{FileAttributes, FileTree}
import sbt.io.FileTreeDataView
import xsbti.compile.analysis.Stamp

val allInputs = taskKey[Seq[File]]("")
val allInputsExplicit = taskKey[Seq[File]]("")

val checkInputs = inputKey[Unit]("")
val checkInputsExplicit = inputKey[Unit]("")

allInputs := (Compile / unmanagedSources / fileInputs).value.all.map(_._1.toFile)

checkInputs := {
  val res = allInputs.value
  val scala = (Compile / scalaSource).value
  val expected = Def.spaceDelimited("<args>").parsed.map(scala / _).toSet
  assert(res.toSet == expected)
}

// In this test we override the FileTree.Repository used by the all method.
allInputsExplicit := {
  val files = scala.collection.mutable.Set.empty[File]
  val underlying = implicitly[FileTree.Repository]
  val repo = new FileTree.Repository {
    override def get(glob: Glob): Seq[(Path, FileAttributes)] = {
      val res = underlying.get(glob)
      files ++= res.map(_._1.toFile)
      res
    }
    override def close(): Unit = {}
  }
  val include = (Compile / unmanagedSources / includeFilter).value
  val _ = (Compile / unmanagedSources / fileInputs).value.all(repo).map(_._1.toFile).toSet
  files.filter(include.accept).toSeq
}

checkInputsExplicit := {
  val res = allInputsExplicit.value
  val scala = (Compile / scalaSource).value
  val expected = Def.spaceDelimited("<args>").parsed.map(scala / _).toSet
  assert(res.toSet == expected)
}
