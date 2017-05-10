commands += Command.command("noop") { s => s }

TaskKey[Unit]("check") := {
  assert(commands.value.toString().contains("SimpleCommand(noop)"),
    s"""commands should contain "SimpleCommand(noop)" in ${commands.value}""")
}
