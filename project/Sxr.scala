import sbt._
import Keys._
import Scope.ThisScope

import sbt.librarymanagement.syntax._

import sbt.internal.inc.RawCompiler

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
    scalacOptions += "-Xplugin:" + managedClasspath.value.files
      .filter(_.getName.contains("sxr"))
      .absString,
    scalacOptions += "-Ystop-after:sxr",
    target := target.in(taskGlobal).value / "browse",
    sxr in taskGlobal := sxrTask.value
  )
  def taskGlobal = ThisScope.copy(task = Global)
  def sxrTask = Def task {
    val out = target.value
    val outputDir = out.getParentFile / (out.getName + ".sxr")
    val f = FileFunction.cached(streams.value.cacheDirectory / "sxr", FilesInfo.hash) { in =>
      streams.value.log.info("Generating sxr output in " + outputDir.getAbsolutePath + "...")
      IO.delete(out)
      IO.createDirectory(out)
      val comp =
        new RawCompiler(scalaInstance.value, classpathOptions.value, streams.value.log)
      comp(in.toSeq.sorted, fullClasspath.value.files, out, scalacOptions.value)
      Set(outputDir)
    }
    f(sources.value.toSet)
    outputDir
  }
}
