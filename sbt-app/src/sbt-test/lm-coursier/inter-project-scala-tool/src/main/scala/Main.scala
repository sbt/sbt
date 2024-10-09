import java.io.File
import java.nio.file.Files


/**
 * Azertyuiopqsdfghjklmwxcvbn
 *
 *  @author A
 *  @param may not be `'''null'''`!!!
 */
object Main extends App {
  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
