scalaOrganization := "org.other"

scalaArtifacts += "cats"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.other" %% "cats" % "0.6.1",
  "org.other" %% "dogs" % "0.4.3"
)

lazy val check = taskKey[Unit]("Runs the check")

check := {
  val lastLog = BuiltinCommands lastLogFile state.value
  val last = IO read lastLog.get
  def containsWarn1 = last contains "Binary version (0.6.1) for dependency org.other#cats_2.11;0.6.1"
  def containsWarn2 = last contains "Binary version (0.4.3) for dependency org.other#dogs_2.11;0.4.3"
  def containsWarn3 = last contains "differs from Scala binary version in project (2.11)."
  if (!containsWarn1) sys error "cats should not be exempted from the Scala binary version check"
  if (containsWarn2)  sys error "dogs should be exempted from the Scala binary version check"
  if (!containsWarn3) sys error "Binary version check failed"
}
