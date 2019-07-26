import java.io.File
import java.nio.file.Files

object Main extends App {
  val p = new java.util.Properties
  p.load(
    Thread.currentThread()
      .getContextClassLoader
      .getResource("common-version-info.properties")
      .openStream()
  )

  val hadoopVersion = p.getProperty("version")
  Console.err.println(s"Found hadoop version $hadoopVersion")

  assert(hadoopVersion.startsWith("3.1."))

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
