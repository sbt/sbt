import sbt._
import Keys._

object Dependencies {
  lazy val scala210 = "2.10.5"
  lazy val scala211 = "2.11.7"

  val bootstrapSbtVersion = "0.13.8"
  // lazy val interfaceProj = "org.scala-sbt" % "interface" % bootstrapSbtVersion
  lazy val ioProj = "org.scala-sbt" % "io" % bootstrapSbtVersion
  // lazy val collectionProj = "org.scala-sbt" % "collections" % bootstrapSbtVersion
  // lazy val logProj = "org.scala-sbt" % "logging" % bootstrapSbtVersion
  // lazy val crossProj = "org.scala-sbt" % "cross" % bootstrapSbtVersion

  lazy val jline = "jline" % "jline" % "2.11"
  // lazy val launcherInterface = "org.scala-sbt" % "launcher-interface" % "1.0.0-M1"
  // lazy val ivy = "org.scala-sbt.ivy" % "ivy" % "2.3.0-sbt-927bc9ded7f8fba63297cddd0d5a3d01d6ad5d8d"
  // lazy val jsch = "com.jcraft" % "jsch" % "0.1.46" intransitive ()
  lazy val sbtSerialization = "org.scala-sbt" %% "serialization" % "0.1.2"
  lazy val sbinary = "org.scala-tools.sbinary" %% "sbinary" % "0.4.2"
  // lazy val junit = "junit" % "junit" % "4.11"
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

  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.11.4"
  lazy val specs2 = "org.specs2" %% "specs2" % "2.3.11"
  lazy val testDependencies = libraryDependencies ++= Seq(
    scalaCheck,
    specs2
  )
}
