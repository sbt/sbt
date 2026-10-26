import sbt.taskprogressretention.Probe

lazy val root = (project in file(".")).settings(Probe.settings)
