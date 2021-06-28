import java.nio.file.Path
import java.util.concurrent.TimeUnit

object RunBinary {
  def apply(binary: Path, args: Seq[String], libraryPath: Path): Seq[String] = {
    val builder = new java.lang.ProcessBuilder(binary.toString +: args :_*)
    if (scala.util.Properties.isLinux) {
      builder.environment.put("LD_LIBRARY_PATH", libraryPath.getParent.toString)
    }
    val process = builder.start()
    process.waitFor(5, TimeUnit.SECONDS)
    scala.io.Source.fromInputStream(process.getInputStream).getLines.toVector ++
      scala.io.Source.fromInputStream(process.getErrorStream).getLines
  }
}
