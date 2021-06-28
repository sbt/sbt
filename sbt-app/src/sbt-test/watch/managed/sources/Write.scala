import java.nio.file._
object Write {
  def main(args: Array[String]): Unit = {
    val dir = Paths.get(args(0))
    Files.write(
      dir.resolve("sources").resolve("Write.scala"),
      Files.readAllBytes(dir.resolve("changes").resolve("Write.scala")),
    )
    val output = dir.resolve("output.txt")
    Files.write(output, "failure".getBytes)
  }
}