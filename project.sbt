sbtPlugin := true

name := "sbt-dependency-graph"

organization := "net.virtual-void"

version := "0.6.1-SNAPSHOT"

homepage := Some(url("http://github.com/jrudolph/sbt-dependency-graph"))

licenses in GlobalScope += "Apache License 2.0" -> url("https://github.com/jrudolph/sbt-dependency-graph/raw/master/LICENSE")

(LsKeys.tags in LsKeys.lsync) := Seq("dependency", "graph", "sbt-plugin", "sbt")

(LsKeys.docsUrl in LsKeys.lsync) <<= homepage

(description in LsKeys.lsync) :=
  "An sbt plugin to visualize dependencies of your build."

libraryDependencies += "com.github.mdr" %% "ascii-graphs" % "0.0.2"
