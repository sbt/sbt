package sbt.watch.termination.reload

import sbt._
import Keys._
import sbt.nio.Keys._
import sbt.nio.Watch

object Build {
  val checkTerminationAction = inputKey[Unit]("Check that watchOnTermination received the expected action")
  val checkTerminationActionImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val Seq(expectedAction) = Def.spaceDelimited().parsed
    val logFile = baseDirectory.value / "action-log.txt"
    val loggedAction = IO.read(logFile).trim
    assert(loggedAction == expectedAction, s"Expected action '$expectedAction', but got '$loggedAction'")
  }

  lazy val root = (project in file(".")).settings(
    checkTerminationAction := checkTerminationActionImpl.evaluated,
    watchOnTermination := { (count: Int, action: Watch.Action) =>
      if (action == Watch.Reload) {
        val logFile = baseDirectory.value / "action-log.txt"
        IO.write(logFile, action.toString)
      }
    },
    watchOnFileInputEvent := { (count: Int, event: Watch.Event) =>
      if (event.path.getFileName.toString == "trigger.txt") {
        val flagFile = baseDirectory.value / "triggered.txt"
        if (!flagFile.exists) {
          IO.write(flagFile, "true")
          Watch.Reload
        } else {
          Watch.CancelWatch
        }
      } else Watch.Ignore
    },
    watchSources += baseDirectory.value / "trigger.txt",
    Compile / compile := {
      val trigger = baseDirectory.value / "trigger.txt"
      IO.write(trigger, System.currentTimeMillis.toString)
      (Compile / compile).value
    }
  )
}
