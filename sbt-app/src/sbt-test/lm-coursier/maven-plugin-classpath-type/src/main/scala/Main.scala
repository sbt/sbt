import java.io.File
import java.nio.file.Files

object Main {
  def main(args: Array[String]): Unit = {
    Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
  }
}
