/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

val marker = taskKey[Unit]("Record the channel that starts a watch")
val followup = taskKey[Unit]("Record the source of a command queued by cleanup")

marker := {
  IO.write(
    baseDirectory.value / "watch-started",
    state.value.source.map(_.channelName).getOrElse("missing")
  )
}

followup := {
  IO.write(
    baseDirectory.value / "watch-followup",
    state.value.source.map(_.channelName).getOrElse("none")
  )
}

watchOnTermination := {
  val base = baseDirectory.value
  (_, command, _, state) =>
    if (command != "marker") state
    else {
      val modeFile = base / "watch-mode"
      val mode = if (modeFile.exists) IO.read(modeFile) else ""
      IO.delete(modeFile)
      IO.write(base / "watch-stopped", state.source.map(_.channelName).getOrElse("missing"))
      mode match {
        case "queue" => "followup" :: state
        case "reboot" | "reboot-framed" =>
          val remaining = List(Exec("followup", None))
          val commands = if (mode == "reboot-framed") {
            Exec("ignored", state.source) ::
              Exec("sbtReportResult 123", state.source) :: remaining
          } else remaining
          try state.copy(exitHooks = Set.empty, remainingCommands = commands).reboot(false)
          catch {
            case reload: xsbti.FullReload =>
              IO.write(base / "watch-reboot", reload.arguments().mkString("\n"))
              state
          }
        case _ => state
      }
    }
}
