package sbtw

import java.io.File
import scala.io.Source
import scala.util.Using

object ConfigLoader:

  def loadLines(file: File): Seq[String] =
    if !file.isFile then Nil
    else
      try
        Using.resource(Source.fromFile(file))(_.getLines().toList.flatMap: line =>
          val trimmed = line.trim
          if trimmed.isEmpty || trimmed.startsWith("#") then Nil
          else Seq(trimmed)
        )
      catch { case _: Exception => Nil }

  def loadSbtOpts(cwd: File, sbtHome: File): Seq[String] =
    val fromProject = new File(cwd, ".sbtopts")
    val fromConfig = new File(sbtHome, "conf/sbtopts")
    val fromEtc = new File("/etc/sbt/sbtopts")
    val fromSbtConfig = new File(sbtHome, "conf/sbtconfig.txt")
    val fromEnv = sys.env.get("SBT_OPTS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    val fromProjectLines = loadLines(fromProject).map(stripJ)
    val fromConfigLines = loadLines(fromConfig)
    val fromEtcLines = loadLines(fromEtc)
    val fromSbtConfigLines = loadLines(fromSbtConfig)
    (fromEtcLines ++ fromConfigLines ++ fromSbtConfigLines ++ fromEnv ++ fromProjectLines)

  def loadJvmOpts(cwd: File): Seq[String] =
    val fromProject = new File(cwd, ".jvmopts")
    val fromEnv = sys.env.get("JAVA_OPTS").toSeq.flatMap(_.split("\\s+").filter(_.nonEmpty))
    fromEnv ++ loadLines(fromProject)

  private def stripJ(s: String): String = if s.startsWith("-J") then s.substring(2).trim else s

  def defaultJavaOpts: Seq[String] = Seq("-Dfile.encoding=UTF-8")
  def defaultSbtOpts: Seq[String] = Nil

  def sbtVersionFromBuildProperties(projectDir: File): Option[String] =
    val f = new File(projectDir, "project/build.properties")
    if !f.isFile then None
    else
      try
        Using.resource(Source.fromFile(f)): src =>
          src
            .getLines()
            .map(_.trim)
            .filterNot(_.startsWith("#"))
            .find(line => line.startsWith("sbt.version") && line.contains("="))
            .flatMap: line =>
              val eq = line.indexOf('=')
              if eq >= 0 then Some(line.substring(eq + 1).trim).filter(_.nonEmpty)
              else None
      catch { case _: Exception => None }

  def isSbtProjectDir(dir: File): Boolean =
    new File(dir, "build.sbt").isFile || new File(dir, "project/build.properties").isFile
end ConfigLoader
