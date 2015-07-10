import sbt._
import Keys._
import Scope.ThisScope

object Sxr {
  val sxrConf = config("sxr").hide
  val sxr = TaskKey[File]("sxr")
  val sourceDirectories = TaskKey[Seq[File]]("sxr-source-directories")

  lazy val settings: Seq[Setting[_]] = inTask(sxr)(inSxrSettings) ++ baseSettings

  def baseSettings = Seq(
    libraryDependencies += "org.scala-sbt.sxr" % "sxr_2.10" % "0.3.0" % sxrConf.name
  )
  def inSxrSettings = Seq(
    managedClasspath := update.value.matching(configurationFilter(sxrConf.name)).classpath,
    scalacOptions += "-P:sxr:base-directory:" + sourceDirectories.value.absString,
    scalacOptions += "-Xplugin:" + managedClasspath.value.files.filter(_.getName.contains("sxr")).absString,
    scalacOptions += "-Ystop-after:sxr",
    target := target.in(taskGlobal).value / "browse",
    sxr in taskGlobal <<= sxrTask
  )
  def taskGlobal = ThisScope.copy(task = Global)
  def sxrTask = (sources, target, scalacOptions, classpathOptions, scalaInstance, fullClasspath, streams) map { (srcs, out, opts, cpOpts, si, cp, s) =>
    val cache = s.cacheDirectory
    val outputDir = out.getParentFile / (out.getName + ".sxr")
    val f = FileFunction.cached(cache / "sxr", FilesInfo.hash) { in =>
      s.log.info("Generating sxr output in " + outputDir.getAbsolutePath + "...")
      IO.delete(out)
      IO.createDirectory(out)
      val comp = new compiler.RawCompiler(si, cpOpts, s.log)
      comp(in.toSeq.sorted, cp.files, out, opts)
      Set(outputDir)
    }
    f(srcs.toSet)
    outputDir
  }
}
