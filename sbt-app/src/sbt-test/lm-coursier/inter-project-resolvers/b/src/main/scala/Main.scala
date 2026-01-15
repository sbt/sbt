import java.io.File
import java.nio.file.Files

object Main {

  // TODO Use some jvm-repr stuff as a test

  def main(args: Array[String]): Unit = {
    Files.write(new File("output").toPath, A.default.msg.getBytes("UTF-8"))
  }
}
