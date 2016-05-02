import sbt._
import Keys._

object Dependencies {
  lazy val scala210 = "2.10.6"
  lazy val scala211 = "2.11.7"

  lazy val sbtIO = "org.scala-sbt" %% "io" % "1.0.0-M3"
  lazy val jline = "jline" % "jline" % "2.13"
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"
  lazy val sbinary = "org.scala-sbt" %% "sbinary" % "0.4.3"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _ => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }

  lazy val scalaXml = scala211Module("scala-xml", "1.0.1")

  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4"
  lazy val scalatest = "org.scalatest" %% "scalatest" % "2.2.4"

  lazy val parserCombinator211 = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
}
