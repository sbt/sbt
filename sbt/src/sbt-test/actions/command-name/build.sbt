val command = Command.command("noop") { s => s }

TaskKey[Unit]("check") := {
  assert(command.commandName == Some("noop"), """command.commandName should be "noop"""")
}
