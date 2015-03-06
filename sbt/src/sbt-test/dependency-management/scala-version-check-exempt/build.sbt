// https://github.com/sbt/sbt/issues/1818

scalaVersion := "2.11.5"
libraryDependencies += "org.scala-lang" %% "scala-actors-migration" % "1.1.0"
libraryDependencies += "org.scala-lang" %% "scala-pickling" % "0.9.1"

lazy val check = taskKey[Unit]("Runs the check")

check := {
  val lastLog = BuiltinCommands lastLogFile state.value
  val last = IO read lastLog.get
  def containsWarn1 = last contains "Binary version (1.1.0) for dependency org.scala-lang#scala-actors-migration_2.11;1.1.0"
  def containsWarn2 = last contains "Binary version (0.9.1) for dependency org.scala-lang#scala-pickling_2.11;0.9.1"
  def containsWarn3 = last contains "differs from Scala binary version in project (2.11)."
  if (containsWarn1 && containsWarn3) sys error "scala-actors-migration isn't exempted from the Scala binary version check"
  if (containsWarn2 && containsWarn3) sys error "scala-pickling isn't exempted from the Scala binary version check"
}
