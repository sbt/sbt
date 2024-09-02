import complete.{ Parser, Parsers }
import Parser._
import Parsers._

lazy val root = (project in file("."))
  .settings(
    crossPaths := false,
    crossScalaVersions := Seq("2.12.20", "2.13.12"),
    scalaVersion := "2.12.20",
    Compile / doc / scalacOptions += "-Xfatal-warnings",
    commands += Command.command("excludeB") { s =>
      val impl = """val src = (sources in Compile).value; src.filterNot(_.getName.contains("B"))"""
      s"set sources in (Compile, doc) := { $impl }" :: s
    },
    commands += Command.arb(_ => ("setDocExtension": Parser[String]) ~> " " ~> matched(any.*)) { (s, filter: String) =>
      val impl = s"""val src = (sources in Compile).value; src.filter(_.getName.endsWith("$filter"))"""
      s"set sources in (Compile, doc) := { $impl }" :: s
    },
  )
