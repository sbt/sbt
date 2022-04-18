lazy val ok = project.settings(
  Compile / mainClass := Some("jartest.Main")
)

lazy val missing = project.settings(
  Compile / mainClass := Some("does.not.exist")
)

lazy val suppressed = project.settings(
  Compile / mainClass := Some("does.not.exist"),
  Compile / allowUndiscoveredMainClass := true
)