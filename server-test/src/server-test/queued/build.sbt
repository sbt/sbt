
commands += Command.command("slowTask") { state =>
  Thread.sleep(5000)
  state
}

commands += Command.command("quickTask") { state =>
  state
}

Global / cancelable := true
