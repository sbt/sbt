import sbt._
import Keys._

object Dependencies {
  lazy val scala211 = "2.11.8"
  lazy val scala212 = "2.12.0-M4"

  private lazy val sbtIO = "org.scala-sbt" %% "io" % "1.0.0-M6"

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID, c: Option[Configuration] = None) =
    path match {
      case Some(f) => p dependsOn c.fold[ClasspathDependency](ProjectRef(file(f), projectName))(ProjectRef(file(f), projectName) % _)
      case None    => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)

  lazy val jline = "jline" % "jline" % "2.13"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.1"
  val scalatest = "org.scalatest" %% "scalatest" % "2.2.6"

  lazy val parserCombinator211 = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

  lazy val sjsonnewVersion = "0.4.2"
  lazy val sjsonnew = "com.eed3si9n" %% "sjson-new-core" % sjsonnewVersion
  lazy val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % sjsonnewVersion
}
