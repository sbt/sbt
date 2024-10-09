import java.io.File
import java.nio.file.Files

object Main extends App {

  // TODO Use some jvm-repr stuff

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
