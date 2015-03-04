// https://github.com/sbt/sbt/issues/1818

scalaVersion := "2.11.5"
libraryDependencies += "org.scala-lang" %% "scala-actors-migration" % "1.1.0"

lazy val check = taskKey[Unit]("Runs the check")
check := {
  val lastLog = BuiltinCommands lastLogFile state.value
  val last = IO read lastLog.get
  def containsWarn1 = last contains "Binary version (1.1.0) for dependency org.scala-lang#scala-actors-migration_2.11;1.1.0"
  def containsWarn2 = last contains "differs from Scala binary version in project (2.11)."
  if (containsWarn1 && containsWarn2) sys error "scala-actors-migration isn't exempted from the Scala binary version check"
}
