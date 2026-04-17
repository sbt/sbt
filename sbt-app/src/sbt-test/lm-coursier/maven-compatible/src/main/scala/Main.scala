import java.io.File
import java.nio.file.Files

object Main {

  // TODO Use some jvm-repr stuff

  def main(args: Array[String]): Unit = {
    Files.writeString(new File("output").toPath, "OK")
  }
}
