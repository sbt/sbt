import java.nio.file.Path

val checkDirectoryContents = inputKey[Unit]("Validates that a directory has the expected files")
checkDirectoryContents := {
  val arguments = Def.spaceDelimited("").parsed
  val directory = (baseDirectory.value / arguments.head).toPath
  val view = fileTreeView.value
  val expected = arguments.tail
  expected match {
    case s if s.isEmpty => assert(view.list(directory.toGlob / **).isEmpty)
    case Seq("empty")   => assert(view.list(directory.toGlob / **).isEmpty)
    case globStrings =>
      val globs = globStrings.map(Glob.apply)
      val actual: Seq[Path] = view.list(directory.toGlob / **).map {
        case (p, _) => directory.relativize(p)
      }
      assert(actual.forall(f => globs.exists(_.matches(f))))
  }
}
