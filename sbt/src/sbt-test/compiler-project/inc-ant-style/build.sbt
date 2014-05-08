logLevel := Level.Debug

incOptions := incOptions.value.withNameHashing(true)

/* Performs checks related to compilations:
 *  a) checks in which compilation given set of files was recompiled
 *  b) checks overall number of compilations performed
 */
TaskKey[Unit]("check-compilations") <<= (compile in Compile, scalaSource in Compile) map { (a: sbt.inc.Analysis, src: java.io.File) =>
  def relative(f: java.io.File): java.io.File =  f.relativeTo(src) getOrElse f
  val allCompilations = a.compilations.allCompilations
  val recompiledFiles: Seq[Set[java.io.File]] = allCompilations map { c =>
    val recompiledFiles = a.apis.internal.collect {
      case (file, api) if api.compilation.startTime == c.startTime => relative(file)
    }
    recompiledFiles.toSet
  }
  def recompiledFilesInIteration(iteration: Int, fileNames: Set[String]) = {
    val files = fileNames.map(new java.io.File(_))
    assert(recompiledFiles(iteration) == files, "%s != %s".format(recompiledFiles(iteration), files))
  }
  assert(allCompilations.size == 2)
  // B.scala and C.scala are compiled at the beginning, in the Ant-style incremental compilation
  // they are not rebuild when A.scala.
  recompiledFilesInIteration(0, Set("B.scala", "C.scala"))
  // A.scala is changed and recompiled
  recompiledFilesInIteration(1, Set("A.scala"))
}
