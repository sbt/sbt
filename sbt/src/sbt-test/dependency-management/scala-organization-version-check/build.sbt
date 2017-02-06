scalaOrganization := "org.other"

scalaArtifacts += "thing"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "org.other" % "thing" % "1.2.3",
  "org.other" %% "wotsit" % "4.5.6"
)

lazy val check = taskKey[Unit]("Runs the check")

check := {
  val lastLog = BuiltinCommands lastLogFile state.value
  val last = IO read lastLog.get
  def containsWarn1 = last contains "Binary version (1.2.3) for dependency org.other#thing;1.2.3"
  def containsWarn2 = last contains "Binary version (4.5.6) for dependency org.other#wotsit_2.11;4.5.6"
  def containsWarn3 = last contains "differs from Scala binary version in project (2.11)."
  if (!containsWarn1) sys error "thing should not be exempted from the Scala binary version check"
  if (containsWarn2)  sys error "wotsit should be exempted from the Scala binary version check"
  if (!containsWarn3) sys error "Binary version check failed"
}
