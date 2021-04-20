package pkg

import java.nio.file.{ Paths, Files }
import java.nio.charset.Charset

object A extends App {
  val out = Paths.get("out.txt")
  val content = sys.props("java.version")
  val w = Files.newBufferedWriter(out, Charset.forName("UTF-8"))
  try {
    w.write(content)
    w.flush()
  } finally {
    w.close
  }
}
