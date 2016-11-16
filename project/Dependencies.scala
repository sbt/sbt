import sbt._
import Keys._

object Dependencies {
  lazy val scala211 = "2.11.8"

  val ioVersion = "1.0.0-M6"
  val utilVersion = "0.1.0-M15"

  private lazy val sbtIO = "org.scala-sbt" %% "io" % ioVersion

  private lazy val utilCollection = "org.scala-sbt" %% "util-collection" % utilVersion
  private lazy val utilLogging    = "org.scala-sbt" %% "util-logging"    % utilVersion
  private lazy val utilTesting    = "org.scala-sbt" %% "util-testing"    % utilVersion
  private lazy val utilCompletion = "org.scala-sbt" %% "util-completion" % utilVersion
  private lazy val utilCache      = "org.scala-sbt" %% "util-cache"      % utilVersion

  def getSbtModulePath(key: String, name: String) = {
    val localProps = new java.util.Properties()
    IO.load(localProps, file("project/local.properties"))
    val path = Option(localProps getProperty key) orElse (sys.props get key)
    path foreach (f => println(s"Using $name from $f"))
    path
  }

  lazy val sbtIoPath   = getSbtModulePath("sbtio.path",   "sbt/io")
  lazy val sbtUtilPath = getSbtModulePath("sbtutil.path", "sbt/util")

  def addSbtModule(p: Project, path: Option[String], projectName: String, m: ModuleID, c: Option[Configuration] = None) =
    path match {
      case Some(f) => p dependsOn c.fold[ClasspathDependency](ProjectRef(file(f), projectName))(ProjectRef(file(f), projectName) % _)
      case None    => p settings (libraryDependencies += c.fold(m)(m % _))
    }

  def addSbtIO(p: Project): Project             = addSbtModule(p, sbtIoPath, "io", sbtIO)

  def addSbtUtilCollection(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilCollection", utilCollection)
  def addSbtUtilLogging(p: Project): Project    = addSbtModule(p, sbtUtilPath, "utilLogging",    utilLogging)
  def addSbtUtilTesting(p: Project): Project    = addSbtModule(p, sbtUtilPath, "utilTesting",    utilTesting, Some(Test))
  def addSbtUtilCompletion(p: Project): Project = addSbtModule(p, sbtUtilPath, "utilComplete",   utilCompletion)
  def addSbtUtilCache(p: Project): Project      = addSbtModule(p, sbtUtilPath, "utilCache",      utilCache)

  lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-2cc8d2761242b072cedb0a04cb39435c4fa24f9a"
  lazy val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }
  lazy val scalaXml = scala211Module("scala-xml", "1.0.5")
  lazy val sjsonnewVersion = "0.4.1"
  lazy val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % sjsonnewVersion

  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _ => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }
}
