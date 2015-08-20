import sbt._
import Keys._
import StringUtilities.normalize

object Util {
  lazy val scalaKeywords = TaskKey[Set[String]]("scala-keywords")
  lazy val generateKeywords = TaskKey[File]("generateKeywords")

  lazy val javaOnlySettings = Seq[Setting[_]](
    compileOrder := CompileOrder.JavaThenScala,
    unmanagedSourceDirectories in Compile <<= Seq(javaSource in Compile).join
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
      val PackageName = "sbt"
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
    generateKeywords <<= (sourceManaged, scalaKeywords) map writeScalaKeywords,
    sourceGenerators <+= generateKeywords map (x => Seq(x))
  ))
}
