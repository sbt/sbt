import complete.DefaultParsers.{ *, given }

LocalRootProject / name := "hello"
scalaVersion := "3.9.0"
autoScalaLibrary := false
crossPaths := false

def logLines(files: List[File]): List[String] =
  files
    .filter(_.exists)
    .flatMap(IO.readLines(_))
    .map(sbt.internal.util.EscHelpers.stripColorsAndMoves)
    .filterNot(_.contains("[debug]"))

def globalLogLines(st: State): List[String] = {
  val backing = st.globalLogging.backing
  logLines(backing.last.toList :+ backing.file)
}

lazy val checkGlobalLogContains = inputKey[Unit]("checks that the global log contains the given string")

checkGlobalLogContains := {
  val expected: String = (Space ~> StringBasic).parsed
  val contents = globalLogLines(state.value).mkString("\n")
  assert(contents.contains(expected), s"missing '$expected' in global logs:\n$contents")
}

lazy val exportFailedSessionLog = taskKey[Unit]("exports the last session of the previous global log, delimited by the welcome banner")

exportFailedSessionLog := Def.uncached {
  val st = state.value
  val t = target.value
  val b = baseDirectory.value.toString
  val lastLog = st.globalLogging.backing.last.getOrElse(sys.error("no previous global log"))
  val chunks: List[List[String]] =
    logLines(lastLog :: Nil)
      .foldLeft(List(List.empty[String])) { (acc, line) =>
        if line.contains("welcome to sbt") then Nil :: acc
        else
          (line
            .replace(b, "BASE")
            .replaceAll(" -{4,}$", "")
            .replaceAll("""\[\d+\.\.\d+\.\.\d+\]""", "[OFFSET]") :: acc.head) :: acc.tail
      }
      .map(_.reverse)
      .reverse
  IO.writeLines(t / "failed-session.log", chunks.last.filter(_.startsWith("[error]")))
}
