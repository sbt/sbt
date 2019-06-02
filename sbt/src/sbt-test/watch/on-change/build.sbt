import java.nio.file._
import java.nio.file.attribute.FileTime

import sbt.nio.Keys._
import sbt.nio._

import scala.concurrent.duration._

watchTriggeredMessage := { (i, path: Path, c) =>
  val prev = watchTriggeredMessage.value
  if (path.getFileName.toString == "C.scala")
    throw new IllegalStateException("C.scala should not trigger")
  prev(i, path, c)
}

watchOnIteration := { i: Int =>
  val base = baseDirectory.value.toPath
  val src =
    base.resolve("src").resolve("main").resolve("scala").resolve("sbt").resolve("test")
  val changes = base.resolve("changes")
  def copy(fileName: String): Unit = {
    val content =
      new String(Files.readAllBytes(changes.resolve(fileName))) + "\n" + ("//" * i)
    Files.write(src.resolve(fileName), content.getBytes)
  }
  val c = src.resolve("C.scala")
  Files.setLastModifiedTime(c, FileTime.fromMillis(Files.getLastModifiedTime(c).toMillis + 1111))
  if (i < 5) copy("A.scala")
  else copy("B.scala")
  println(s"Waiting for changes...")
  Watch.Ignore
}

watchOnFileInputEvent := { (_, event: Watch.Event) =>
  if (event.path.getFileName.toString == "B.scala") Watch.CancelWatch
  else Watch.Trigger
}

watchAntiEntropy := 0.millis