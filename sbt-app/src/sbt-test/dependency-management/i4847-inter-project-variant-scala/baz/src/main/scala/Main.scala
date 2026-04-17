import java.nio.file.Files
import java.nio.file.Paths

object Main {
  def main(args: Array[String]): Unit = {
    val msg = Bar.value
    Files.writeString(Paths.get("baz/output"), msg)
  }
}
