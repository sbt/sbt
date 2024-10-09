import java.io.File
import java.nio.file.Files

import shapeless._

object Main extends App {
  case class CC(s: String)
  val cc = CC("OK")
  val l = Generic[CC].to(cc)
  val msg = l.head

  Files.write(new File("output").toPath, msg.getBytes("UTF-8"))
}
