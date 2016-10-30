import java.io.File
import java.nio.file.Files

object Main extends App {
  // Not using directly the NetLogo 5.x lib, which seems to depend on Scala 2.9
  // Can't find a way to check that NetLogo.jar is in the classpath
  // These don't work, even with fork := true:
  // assert(Thread.currentThread.getContextClassLoader.getResource("org/nlogo/nvm/Task.class") != null)
  // Thread.currentThread.getContextClassLoader.getResource("org/nlogo/nvm/Task.class")
  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
