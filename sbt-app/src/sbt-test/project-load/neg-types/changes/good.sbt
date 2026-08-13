import complete.DefaultParsers.{ *, given }

LocalRootProject / name := "hello"
scalaVersion := "3.8.4"
autoScalaLibrary := false
crossPaths := false

def globalLogLines(st: State): List[String] = {
  val backing = st.globalLogging.backing
  val logs = (backing.last.toList :+ backing.file).filter(_.exists)
  logs
    .flatMap(IO.readLines(_))
    .map(sbt.internal.util.EscHelpers.stripColorsAndMoves)
    .filterNot(_.contains("[debug]"))
}

lazy val checkGlobalLogContains = inputKey[Unit]("checks that the global log contains the given string")

checkGlobalLogContains := {
  val expected: String = (Space ~> StringBasic).parsed
  val contents = globalLogLines(state.value).mkString("\n")
  assert(contents.contains(expected), s"missing '$expected' in global logs:\n$contents")
}

lazy val exportGlobalLog = inputKey[Unit]("logs the nth global log session, delimited by the welcome banner")

exportGlobalLog := {
  val arg: Int = (Space ~> IntBasic).parsed
  val st = state.value
  val t = target.value
  val b = baseDirectory.value.toString
  val chunks: List[List[String]] =
    globalLogLines(st)
      .foldLeft(List(List.empty[String])) { (acc, line) =>
        if line.contains("welcome to sbt") then Nil :: acc
        else (line.replace(b, "BASE").replaceAll(" -{4,}$", "") :: acc.head) :: acc.tail
      }
      .map(_.reverse)
      .reverse
  assert(arg < chunks.size, s"session $arg out of range: ${chunks.size} sessions")
  // st.log.info(chunks.toString)
  IO.writeLines(t / s"session${arg}.log", chunks(arg))
}
