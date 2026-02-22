import sbt.internal.util.complete.Parser

lazy val root = (project in file("."))
  .settings(
    commands += Command.command("checkJavaPlusPlusCompletions")(checkJavaPlusPlusCompletions)
  )

def checkJavaPlusPlusCompletions(state: State): State =
  val appendStrings = Parser
    .completions(state.combinedParser, "java++ ", 9)
    .get
    .map(_.append)
    .toSet
  assert(appendStrings.size <= 100, s"java++ completions must be bounded (got ${appendStrings.size}), see #4310")
  state
