import sbt._
import Keys._
import sbt.contraband.ContrabandPlugin.autoImport._

object Dependencies {
  val scala212 = "2.12.10"

  def nightlyVersion: Option[String] = sys.props.get("sbt.build.version")

  private val ioVersion = nightlyVersion.getOrElse("1.3.1")
  private val utilVersion = nightlyVersion.getOrElse("1.3.2")

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
  val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-cb9cc189e9f3af519f9f102e6c5d446488ff6832"

  val sbtV = "1.0"
  val scalaV = "2.12"

  val jsch = "com.jcraft" % "jsch" % "0.1.54" intransitive ()
  val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
  val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  val scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.6-SNAP5"
  val scalaVerify = "com.eed3si9n.verify" %% "verify" % "0.1.0"
  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.0"
  val sjsonnew = Def.setting {
    "com.eed3si9n" %% "sjson-new-core" % contrabandSjsonNewVersion.value
  }
  val sjsonnewScalaJson = Def.setting {
    "com.eed3si9n" %% "sjson-new-scalajson" % contrabandSjsonNewVersion.value
  }
  val gigahorseOkhttp = "com.eed3si9n" %% "gigahorse-okhttp" % "0.5.0"
  val okhttpUrlconnection = "com.squareup.okhttp3" % "okhttp-urlconnection" % "3.7.0"
  val silencerPlugin = "com.github.ghik" %% "silencer-plugin" % "1.4.1"
}
