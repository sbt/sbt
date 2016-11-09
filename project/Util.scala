import sbt._
import Keys._

object Util {
  lazy val scalaKeywords = TaskKey[Set[String]]("scala-keywords")
  lazy val generateKeywords = TaskKey[File]("generateKeywords")

  lazy val javaOnlySettings = Seq[Setting[_]](
    crossPaths := false,
    compileOrder := CompileOrder.JavaThenScala,
    unmanagedSourceDirectories in Compile := Seq((javaSource in Compile).value),
    crossScalaVersions := Seq(Dependencies.scala211),
    autoScalaLibrary := false
  )

  def getScalaKeywords: Set[String] =
    {
      val g = new scala.tools.nsc.Global(new scala.tools.nsc.Settings)
      g.nme.keywords.map(_.toString)
    }
  def writeScalaKeywords(base: File, keywords: Set[String]): File =
    {
      val init = keywords.map(tn => '"' + tn + '"').mkString("Set(", ", ", ")")
      val ObjectName = "ScalaKeywords"
      val PackageName = "sbt.internal.util"
      val keywordsSrc =
        """package %s
object %s {
  val values = %s
}""".format(PackageName, ObjectName, init)
      val out = base / PackageName.replace('.', '/') / (ObjectName + ".scala")
      IO.write(out, keywordsSrc)
      out
    }
  def keywordsSettings: Seq[Setting[_]] = inConfig(Compile)(Seq(
    scalaKeywords := getScalaKeywords,
    generateKeywords := writeScalaKeywords(sourceManaged.value, scalaKeywords.value),
    sourceGenerators += (generateKeywords map (x => Seq(x))).taskValue
  ))
}
