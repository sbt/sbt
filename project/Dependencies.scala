import sbt._
import Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  val scala211 = "2.11.11"
  val scala212 = "2.12.2"

  private val ioVersion = "1.0.0-M13"
  private val utilVersion = "1.0.0-M27"

  private val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private val utilPosition = "org.scala-sbt" %% "util-position" % utilVersion
  private val utilLogging = "org.scala-sbt" %% "util-logging" % utilVersion
  private val utilCache = "org.scala-sbt" %% "util-cache" % utilVersion

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath = getSbtModulePath("sbtio.path", "sbt/io")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID) =
    path match {
      case Some(f) => p dependsOn ProjectRef(file(f), projectName)
      case None    => p settings (libraryDependencies += m)
    }

  def addSbtIO(p: Project): Project = addSbtModule(p, sbtIoPath, "io", sbtIO)
  def addSbtUtilPosition(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilPosition", utilPosition)
  def addSbtUtilLogging(p: Project): Project =
    addSbtModule(p, sbtUtilPath, "utilLogging", utilLogging)
  def addSbtUtilCache(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilCache", utilCache)

  val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-a3314352b638afbf0dca19f127e8263ed6f898bd"
  val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  val scalaXml = scala211Module("scala-xml", "1.0.5")
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" % Test
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
  val sjsonnew = Def.setting { "com.eed3si9n" %% "sjson-new-core" % contrabandSjsonNewVersion.value }
  val sjsonnewScalaJson = Def.setting { "com.eed3si9n" %% "sjson-new-scalajson" % contrabandSjsonNewVersion.value }
  val gigahorseOkhttp = "com.eed3si9n" %% "gigahorse-okhttp" % "0.3.0"
  val okhttpUrlconnection = "com.squareup.okhttp3" % "okhttp-urlconnection" % "3.7.0"

  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _                                                       => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }
}
