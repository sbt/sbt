import java.io.File
import java.nio.file.Files

import shapeless._

object Main {
  case class CC(s: String)

  def main(args: Array[String]): Unit = {
    val cc = CC("OK")
    val l = Generic[CC].to(cc)
    val msg = l.head

    Files.writeString(new File("output").toPath, msg)
  }
}
