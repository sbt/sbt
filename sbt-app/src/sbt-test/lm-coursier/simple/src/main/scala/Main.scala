import java.io.File
import java.nio.file.Files

object Main {
  def main(args: Array[String]): Unit = {
    Files.writeString(new File("output").toPath, "OK")
  }
}
