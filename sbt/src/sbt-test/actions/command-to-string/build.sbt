commands += Command.command("noop") { s => s }

TaskKey[Unit]("check") := {
  assert(commands.value.toString() == "List(SimpleCommand(noop))",
    s"""commands should display "List(SimpleCommand(noop))" but is ${commands.value}""")
}
