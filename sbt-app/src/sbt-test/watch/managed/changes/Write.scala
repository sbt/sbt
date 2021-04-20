import java.nio.file._

object Write {
  def main(args: Array[String]): Unit = {
    Files.write(Paths.get(args(0)).resolve("output.txt"), "ok".getBytes)
  }
}