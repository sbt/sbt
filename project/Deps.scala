
import sbt._
import sbt.Keys._

object Deps {

  def quasiQuotes = "org.scalamacros" %% "quasiquotes" % "2.1.0"
  def fastParse = "com.lihaoyi" %% "fastparse" % "0.4.2"
  def jsoup = "org.jsoup" % "jsoup" % "1.10.2"
  def scalaXml = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
  def scalazConcurrent = "org.scalaz" %% "scalaz-concurrent" % SharedVersions.scalaz
  def caseApp = "com.github.alexarchambault" %% "case-app" % "1.1.3"
  def http4sBlazeServer = "org.http4s" %% "http4s-blazeserver" % SharedVersions.http4s
  def http4sDsl = "org.http4s" %% "http4s-dsl" % SharedVersions.http4s
  def slf4jNop = "org.slf4j" % "slf4j-nop" % "1.7.22"
  def okhttpUrlConnection = "com.squareup.okhttp" % "okhttp-urlconnection" % "2.7.5"
  def sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0"
  def typesafeConfig = "com.typesafe" % "config" % "1.3.1"

  def scalaAsync = Def.setting {

    val version =
      if (scalaBinaryVersion.value == "2.10") "0.9.5"
      else "0.9.6"

    "org.scala-lang.modules" %% "scala-async" % version
  }
  
  def jarjar = Def.setting {
    val coursierJarjarVersion = "1.0.1-coursier-SNAPSHOT"
    def coursierJarjarFoundInM2 = (file(sys.props("user.home")) / s".m2/repository/org/anarres/jarjar/jarjar-core/$coursierJarjarVersion").exists()

    val jarjarVersion =
      if (sys.env.contains("CI") || coursierJarjarFoundInM2 || !isSnapshot.value)
        coursierJarjarVersion
      else {
        val fallback = "1.0.0"

        // streams.value.log.warn( // "a setting cannot depend on a task"
        scala.Console.err.println(
         s"""Warning: using jarjar $fallback, which doesn't properly shade Scala JARs (classes with '$$' aren't shaded).
            |See the instructions around
            |https://github.com/coursier/coursier/blob/630a780487d662dd994ed1c3246895a22c00cf21/scripts/travis.sh#L40
            |to use a version fine with Scala JARs.""".stripMargin
        )

        fallback
      }

    "org.anarres.jarjar" % "jarjar-core" % jarjarVersion
  }

  def jarjarTransitiveDeps = Seq(
    "com.google.code.findbugs" % "jsr305" % "2.0.2",
    "org.ow2.asm" % "asm-commons" % "5.0.3",
    "org.ow2.asm" % "asm-util" % "5.0.3",
    "org.slf4j" % "slf4j-api" % "1.7.12"
  )
}