import java.io.File
import java.nio.file.Files

import scalaz.stream._
import scalaz.concurrent.Task

object Main extends App {

  val pch = Process.constant((i:Int) => Task.now(())).take(3)
  val count = Process.constant(1).toSource.to(pch).runLog.run.size
  assert(count == 3)

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
