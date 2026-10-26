import java.nio.file.Files
import sbt.internal.FileChangesMacro.inputFiles
import java.nio.file.attribute.FileTime

import scala.concurrent.duration._

@transient
val foo = taskKey[Unit]("foo.txt")
foo / watchForceTriggerOnAnyChange := true
foo / fileInputs := baseDirectory.value.toGlob / "files" / "foo.txt" :: Nil
foo / watchTriggers := baseDirectory.value.toGlob / ** / "foo.txt" :: Nil
foo := {
  foo.inputFiles.foreach { p =>
    Files.setLastModifiedTime(p, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis + 3000))
  }
  sbt.nio.Stamps.check(foo).value
}

watchAntiEntropy := 0.seconds
watchOnFileInputEvent := { (count, event: Watch.Event) =>
  assert(event.path.getFileName.toString == "foo.txt")
  if (Files.readString(event.path) == "foo") {
    if (count < 3) Watch.Trigger
    else Watch.CancelWatch
  } else new Watch.HandleError(new IllegalStateException("Wrong stamp was set"))
}
