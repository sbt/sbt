import java.io.File
import java.nio.file.Files


/**
 * Azertyuiopqsdfghjklmwxcvbn
 *
 *  @author A
 *  @param may not be `'''null'''`!!!
 */
object Main {
  def main(args: Array[String]): Unit = {
    Files.writeString(new File("output").toPath, "OK")
  }
}
