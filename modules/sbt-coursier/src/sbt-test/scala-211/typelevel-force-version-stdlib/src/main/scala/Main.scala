import java.io.File
import java.nio.file.Files

object Main extends App {
  val n': 2.type = 2

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
