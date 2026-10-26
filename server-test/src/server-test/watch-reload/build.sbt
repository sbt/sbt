/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import sbt.internal.FileChangesMacro.inputFiles

@transient
lazy val marker = taskKey[Unit]("Record a watched input and its channel")
@transient
lazy val other = taskKey[Unit]("Record a second watch")
@transient
lazy val callbackQueued = taskKey[Unit]("Record a command queued by watch termination")

name := {
  val file = baseDirectory.value / "build-name"
  if file.exists then IO.read(file) else "before"
}

Global / onChangedBuildSource := {
  if ((LocalRootProject / baseDirectory).value / "automatic-reload").exists then ReloadOnSourceChanges
  else IgnoreSourceChanges
}

def record(base: File, state: State, command: String): Unit =
  val owner = state.source.map(_.channelName).getOrElse("missing")
  IO.write(base / s"iteration-$command", s"$owner:${IO.read(base / "input")}")
  IO.append(base / "events", s"run:$command:$owner\n")

marker / fileInputs := (baseDirectory.value.toGlob / "input") :: Nil
other / fileInputs := (baseDirectory.value.toGlob / "input") :: Nil
other / onChangedBuildSource := IgnoreSourceChanges
marker := {
  val _ = marker.inputFiles
  record(baseDirectory.value, state.value, "marker")
}
other := {
  val _ = other.inputFiles
  record(baseDirectory.value, state.value, "other")
}

callbackQueued := {
  val base = baseDirectory.value
  val st = state.value
  val owner = st.get(AttributeKey[String]("watch-reload-callback-state")).getOrElse("missing")
  IO.write(base / "callback-queued", s"$owner:${st.source.map(_.channelName).getOrElse("none")}")
}

watchOnTermination := {
  val previous = sbt.WatchReloadSupport.onTermination
  val base = baseDirectory.value
  val capturedName = name.value
  (action, command, count, state) =>
    val owner = state.source.map(_.channelName).getOrElse("missing")
    val currentName = Project.extract(state).get(name)
    IO.append(base / "events", s"stop:$command:$owner:$capturedName:$currentName:$action\n")
    val fail = base / "fail-termination"
    if fail.exists then
      IO.delete(fail)
      IO.append(base / "events", s"throw:$command:$owner\n")
      sys.error("watch termination failed")
    val next = if action == Watch.Reload then state else previous(action, command, count, state)
    if (base / "queue-callback").exists then
      "callbackQueued" :: next.put(AttributeKey[String]("watch-reload-callback-state"), owner)
    else next
}

Global / onUnload := {
  val previous = (Global / onUnload).value
  val base = (LocalRootProject / baseDirectory).value
  val capturedName = (LocalRootProject / name).value
  state =>
    IO.append(base / "events", s"unload:$capturedName\n")
    val fail = base / "fail-unload"
    if fail.exists then
      IO.delete(fail)
      sys.error("build unload failed")
    previous(state)
}

Global / onLoad := {
  val previous = (Global / onLoad).value
  val base = (LocalRootProject / baseDirectory).value
  state =>
    val fail = base / "fail-load"
    if fail.exists then
      IO.delete(fail)
      sys.error("build load hook failed")
    previous(state)
}
