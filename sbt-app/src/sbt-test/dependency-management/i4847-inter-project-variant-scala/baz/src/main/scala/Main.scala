import java.nio.file.Files
import java.nio.file.Paths

object Main {
  def main(args: Array[String]): Unit = {
    val msg = Bar.value
    Files.write(Paths.get("baz/output"), msg.getBytes("UTF-8"))
  }
}
