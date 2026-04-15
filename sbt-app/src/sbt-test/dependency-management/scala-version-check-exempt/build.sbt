// https://github.com/sbt/sbt/issues/1818

ThisBuild / scalaVersion := "2.11.12"
libraryDependencies += "org.scala-lang" %% "scala-actors-migration" % "1.1.0"
libraryDependencies += "org.scala-lang" %% "scala-pickling" % "0.9.1"

// This is a workaround for https://github.com/coursier/coursier/issues/3525
// [info] [error] lmcoursier.internal.shaded.coursier.params.rule.SameVersion$CantForceSameVersion:
// Can't force version 2.11.12 for modules org.scala-lang:scala-compiler, org.scala-lang:scala-reflect, org.scala-lang:scala-library
// ....
// [info] [error] Caused by: lmcoursier.internal.shaded.coursier.params.rule.SameVersion$SameVersionConflict:
// Unsatisfied rule SameVersion(HashSet(ModuleMatcher(org.scala-lang:scala-compiler), ModuleMatcher(org.scala-lang:scalap),
// ModuleMatcher(org.scala-lang:scala-actors), ModuleMatcher(org.scala-lang:scala-reflect), ModuleMatcher(org.scala-lang:scala-library))):
// Found versions 2.11.0, 2.11.12 for org.scala-lang:scala-actors, org.scala-lang:scala-compiler, org.scala-lang:scala-library, org.scala-lang:scala-reflect
libraryDependencies += "org.scala-lang" % "scala-actors" % "2.11.12"

lazy val check = taskKey[Unit]("Runs the check")

check := {
  val lastLog = BuiltinCommands.lastLogFile(state.value)
  val last = IO read lastLog.get
  def containsWarn1 = last.contains("Binary version (1.1.0) for dependency org.scala-lang#scala-actors-migration_2.11;1.1.0")
  def containsWarn2 = last.contains("Binary version (0.9.1) for dependency org.scala-lang#scala-pickling_2.11;0.9.1")
  def containsWarn3 = last.contains("differs from Scala binary version in project (2.11).")
  if (containsWarn1 && containsWarn3) sys error "scala-actors-migration isn't exempted from the Scala binary version check"
  if (containsWarn2 && containsWarn3) sys error "scala-pickling isn't exempted from the Scala binary version check"
}
