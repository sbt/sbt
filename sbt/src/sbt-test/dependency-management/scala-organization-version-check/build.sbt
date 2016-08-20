scalaOrganization := "org.other"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.other" %% "thing" % "1.2.3"
)

lazy val check = taskKey[Unit]("Runs the check")

check := {
  val lastLog = BuiltinCommands lastLogFile state.value
  val last = IO read lastLog.get
  def containsWarn1 = last contains "Binary version (1.2.3) for dependency org.other#thing_2.11;1.2.3"
  def containsWarn2 = last contains "differs from Scala binary version in project (2.11)."
  if (containsWarn1) sys error "thing should be exempted from the Scala binary version check"
  if (containsWarn2) sys error "Binary version check failed"
}
