import sbt._
import Keys._

object Dependencies {
  lazy val scala211 = "2.11.8"
  lazy val scala212 = "2.12.0-M4"

  lazy val sbtIO = "org.scala-sbt" %% "io" % "1.0.0-M6"
  lazy val jline = "jline" % "jline" % "2.13"
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"

  lazy val scalaCompiler = Def.setting { "org.scala-lang" % "scala-compiler" % scalaVersion.value }
  lazy val scalaReflect = Def.setting { "org.scala-lang" % "scala-reflect" % scalaVersion.value }

  private def scala211Module(name: String, moduleVersion: String) =
    Def.setting {
      scalaVersion.value match {
        case sv if (sv startsWith "2.9.") || (sv startsWith "2.10.") => Nil
        case _ => ("org.scala-lang.modules" %% name % moduleVersion) :: Nil
      }
    }

  lazy val scalaXml = scala211Module("scala-xml", "1.0.5")

  val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.13.1"
  val scalatest = "org.scalatest" %% "scalatest" % "2.2.6"

  lazy val parserCombinator211 = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"

  lazy val datatypeCodecs = "org.scala-sbt" %% "datatype-codecs" % "1.0.0-SNAPSHOT"
  lazy val sjsonnewScalaJson = "com.eed3si9n" %% "sjson-new-scalajson" % "0.4.0"
}
