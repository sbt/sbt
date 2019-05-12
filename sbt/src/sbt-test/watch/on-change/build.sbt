import java.nio.file._
import sbt.nio.Keys._
import sbt.nio._
import scala.concurrent.duration._
import StandardCopyOption.{ REPLACE_EXISTING => replace }

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
  Files.copy(changes.resolve("C.scala"), src.resolve("C.scala"), replace)
  if (i < 5) {
    val content =
      new String(Files.readAllBytes(changes.resolve("A.scala"))) + "\n" + ("//" * i)
    Files.write(src.resolve("A.scala"), content.getBytes)
  } else {
    Files.copy(changes.resolve("B.scala"), src.resolve("B.scala"), replace)
  }
  println(s"Waiting for changes...")
  Watch.Ignore
}

watchOnFileInputEvent := { (_, event: Watch.Event) =>
  if (event.path.getFileName.toString == "B.scala") Watch.CancelWatch
  else Watch.Trigger
}

watchAntiEntropy := 0.millis