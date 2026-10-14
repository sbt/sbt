/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

@transient
lazy val marker = taskKey[Unit]("Record the channel that starts a watch")

marker := {
  IO.write(
    baseDirectory.value / "watch-started",
    state.value.source.map(_.channelName).getOrElse("missing")
  )
}

watchOnTermination := {
  val base = baseDirectory.value
  (_, _, _, state) =>
    IO.write(base / "watch-stopped", state.source.map(_.channelName).getOrElse("missing"))
    state
}
