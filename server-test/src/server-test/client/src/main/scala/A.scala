import java.nio.file.{ Files, Paths }
import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

object A {
  def main(args: Array[String]): Unit = {
    val path = Paths.get(args.head)
    Files.write(path, "start".getBytes)
    val syncPath = Paths.get(path + ".sync")
    val limit = 10.seconds.fromNow
    while (!Files.exists(syncPath) && !limit.isOverdue) Thread.sleep(10)
    if (limit.isOverdue) throw new TimeoutException
    Files.write(path, "finish".getBytes)
  }
}
