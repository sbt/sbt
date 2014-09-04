
//////////////////////////////////////
// Root project settings
//////////////////////////////////////

name := Common.PROJECT_NAME

unidocSettings

Common.commonSettings

//////////////////////////////////////
// Subproject definitions
//////////////////////////////////////

lazy val core = Common.subproject("core").dependsOn(util)

lazy val util = Common.subproject("util")

//////////////////////////////////////
// Common settings shared by all projects
//////////////////////////////////////

version in ThisBuild := Common.PROJECT_VERSION

organization in ThisBuild := Common.ORGANIZATION

scalaVersion in ThisBuild := Common.SCALA_VERSION

libraryDependencies in ThisBuild ++= Seq(
      "org.scala-lang" % "scala-reflect" % Common.SCALA_VERSION,
      "org.slf4j" % "slf4j-api" % Common.SLF4J_VERSION,
      "ch.qos.logback" % "logback-classic" % Common.LOGBACK_VERSION % "runtime",
      "org.scalatest" %% "scalatest" % Common.SCALATEST_VERSION % "test"
    )

scalacOptions in (ThisBuild, Compile) ++= Seq(
      "-unchecked",
      "-deprecation",
      "-feature"
    )

parallelExecution in (ThisBuild, Test) := false

fork in (ThisBuild, Test) := true

//////////////////////////////////////
// Publishing settings
//////////////////////////////////////

publishMavenStyle in (ThisBuild) := true

pomIncludeRepository in (ThisBuild) := { _ => false }

licenses in (ThisBuild) := Seq(
      "BSD-style" -> url("http://opensource.org/licenses/BSD-2-Clause")
    )

homepage in (ThisBuild) := Some(url("http://genslerappspod.github.io/scalavro/"))

pomExtra in (ThisBuild) := (
      <scm>
        <url>git@github.com:GenslerAppsPod/scalavro.git</url>
        <connection>scm:git:git@github.com:GenslerAppsPod/scalavro.git</connection>
      </scm>
      <developers>
        <developer>
          <id>ConnorDoyle</id>
          <name>Connor Doyle</name>
          <url>http://gensler.com</url>
        </developer>
      </developers>
    )

publishTo in (ThisBuild) <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
