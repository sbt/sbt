import java.io.File
import java.nio.file.Files

object Main {
  def main(args: Array[String]): Unit = {
    val p = new java.util.Properties
    p.load(
      Thread.currentThread()
        .getContextClassLoader
        .getResource("common-version-info.properties")
        .openStream()
    )

    val hadoopVersion = p.getProperty("version")
    Console.err.println(s"Found hadoop version $hadoopVersion")

    assert(hadoopVersion == "2.6.0")

    Files.writeString(new File("output").toPath, "OK")
  }
}
