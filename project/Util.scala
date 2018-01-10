import sbt._
import Keys._
import sbt.IO

import java.io.File
import xsbti.compile.CompileAnalysis

object Util {

  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean =
    IO.read(propFile).contains(versionLine(version))
  def lastCompilationTime(analysis0: CompileAnalysis): Long = {
    val analysis = analysis0 match { case a: sbt.internal.inc.Analysis => a }
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.getStartTime) getOrElse 0L
  }

  def generateVersionFile(version: String,
                          dir: File,
                          s: TaskStreams,
                          analysis: CompileAnalysis): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / "xsbt.version.properties"
    // TODO: replace lastModified() with sbt.io.IO.getModifiedTimeOrZero(), once the build
    // has been upgraded to a version of sbt that includes that call.
    if (!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)) {
      s.log.info("Writing version information to " + f + " :\n" + content)
      IO.write(f, content)
    }
    f :: Nil
  }
}
