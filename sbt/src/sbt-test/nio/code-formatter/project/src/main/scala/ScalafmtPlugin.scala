import java.io.PrintWriter
import java.nio.file._
import sbt._
import sbt.Keys.{ baseDirectory, unmanagedSources }
import sbt.nio.Keys.{ fileInputs, inputFileStamps, outputFileStamper, outputFileStamps }
import sbt.nio.FileStamper
import org.scalafmt.interfaces.{ Scalafmt, ScalafmtReporter }

object ScalafmtPlugin extends AutoPlugin {
  private val reporter = new ScalafmtReporter {
    override def error(file: Path, message: String): Unit = throw new Exception(s"$file $message")
    override def error(file: Path, e: Throwable): Unit = throw e
    override def excluded(file: Path): Unit = {}
    override def parsedConfig(config: Path, scalafmtVersion: String): Unit = {}
    override def downloadWriter: PrintWriter = new PrintWriter(System.out, true)
  }
  private val formatter = Scalafmt.create(this.getClass.getClassLoader).withReporter(reporter)
  object autoImport {
    val scalafmtImpl = taskKey[Seq[Path]]("Format scala sources")
    val scalafmt = taskKey[Unit]("Format scala sources and validate results")
  }
  import autoImport._
  override lazy val projectSettings = super.projectSettings ++ Seq(
    Compile / scalafmtImpl / fileInputs := (Compile / unmanagedSources / fileInputs).value,
    Compile / scalafmtImpl / outputFileStamper := FileStamper.Hash,
    Compile / scalafmtImpl := {
      val config = baseDirectory.value.toPath / ".scalafmt.conf"
      val allInputStamps = (Compile / scalafmtImpl / inputFileStamps).value
      val previous =
        (Compile / scalafmtImpl / outputFileStamps).previous.map(_.toMap).getOrElse(Map.empty)
      allInputStamps.flatMap {
        case (p, s) if previous.get(p).fold(false)(_ == s) => Some(p)
        case (p, s) =>
          try {
            println(s"Formatting $p")
            Files.write(p, formatter.format(config, p, new String(Files.readAllBytes(p))).getBytes)
            Some(p)
          } catch {
            case e: Exception =>
              println(e)
              None
          }
      }
    },
    Compile / scalafmt := {
      val outputs = (Compile / scalafmtImpl / outputFileStamps).value.toMap
      val improperlyFormatted = (Compile / scalafmtImpl).inputFiles.filterNot(outputs.contains _)
      if (improperlyFormatted.nonEmpty) {
        val msg = s"There were improperly formatted files:\n${improperlyFormatted mkString "\n"}"
        throw new IllegalStateException(msg)
      }
    },
    Compile / unmanagedSources / inputFileStamps :=
      (Compile / unmanagedSources / inputFileStamps).dependsOn(Compile / scalafmt).value
  )
}
