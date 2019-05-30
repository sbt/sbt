import java.nio.file.Files

import sbt.nio.Watch

import scala.concurrent.duration._

Compile / sourceGenerators += Def.task {
  baseDirectory.value / "sources" / "Write.scala" :: Nil
}.taskValue

val runTest = taskKey[Unit]("run the test")
runTest := Def.taskDyn {
  val args = s" ${baseDirectory.value}"
  (Runtime / run).toTask(args)
}.value

runTest / watchTriggers += baseDirectory.value.toGlob / "*.txt"
watchAntiEntropy := 0.milliseconds
watchOnFileInputEvent := { (count, e) =>
  if (new String(Files.readAllBytes(e.path)) == "ok") Watch.CancelWatch
  else if (count < 2) Watch.Trigger
  else new Watch.HandleError(new IllegalStateException(s"Wrong event triggered the build: $e"))
}