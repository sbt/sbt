import sbt.internal.FileTree
import sbt.io.FileTreeDataView
import xsbti.compile.analysis.Stamp

val allInputs = taskKey[Seq[File]]("")
val allInputsExplicit = taskKey[Seq[File]]("")

val checkInputs = inputKey[Unit]("")
val checkInputsExplicit = inputKey[Unit]("")

allInputs := (Compile / unmanagedSources / inputs).value.all

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
    override def get(glob: Glob): Seq[FileTreeDataView.Entry[Stamp]] = {
      val res = underlying.get(glob)
      files ++= res.map(_.typedPath.toPath.toFile)
      res
    }
    override def close(): Unit = {}
  }
  val include = (Compile / unmanagedSources / includeFilter).value
  val _ = (Compile / unmanagedSources / inputs).value.all(repo).toSet
  files.filter(include.accept).toSeq
}

checkInputsExplicit := {
  val res = allInputsExplicit.value
  val scala = (Compile / scalaSource).value
  val expected = Def.spaceDelimited("<args>").parsed.map(scala / _).toSet
  assert(res.toSet == expected)
}
