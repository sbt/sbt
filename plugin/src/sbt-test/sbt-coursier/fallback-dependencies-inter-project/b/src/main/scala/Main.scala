import java.io.File
import java.nio.file.Files

object Main extends App {

  val msg = shapeless.Generic[A].to(A.default).head
  
  Files.write(new File("output").toPath, msg.getBytes("UTF-8"))
}
