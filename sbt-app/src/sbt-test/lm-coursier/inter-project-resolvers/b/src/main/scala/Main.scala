import java.io.File
import java.nio.file.Files

object Main extends App {

  // TODO Use some jvm-repr stuff as a test

  Files.write(new File("output").toPath, A.default.msg.getBytes("UTF-8"))
}
