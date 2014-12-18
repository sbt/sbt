import sbt._
import Keys._

object Dependencies {
  lazy val jline        = "jline" % "jline" % "2.11"
  lazy val ivy          = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-fccfbd44c9f64523b61398a0155784dcbaeae28f"
  lazy val jsch         = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val sbinary      = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  lazy val json4sNative = "org.json4s" %% "json4s-native" % "3.2.10"
  lazy val jawnParser   = "org.spire-math" %% "jawn-parser" % "0.6.0"
  lazy val jawnJson4s   = "org.spire-math" %% "json4s-support" % "0.6.0"
  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val testInterface = "org.scala-sbt" % "test-interface" % "1.0"
  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _ => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }
  lazy val scalaXml     = scala211Module("scala-xml", "1.0.1")
  lazy val scalaParsers = scala211Module("scala-parser-combinators", "1.0.1")
}
