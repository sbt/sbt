sbtPlugin := true

name := "sbt-dependency-graph"

organization := "net.virtual-void"

version := "0.5.3-SNAPSHOT"

homepage := Some(url("http://github.com/jrudolph/sbt-dependency-graph"))

licenses in GlobalScope += "Apache License 2.0" -> url("https://github.com/jrudolph/sbt-dependency-graph/raw/master/LICENSE")

(LsKeys.tags in LsKeys.lsync) := Seq("dependency", "graph", "sbt-plugin", "sbt")

(LsKeys.docsUrl in LsKeys.lsync) <<= homepage

(description in LsKeys.lsync) :=
  "An sbt plugin which allows to create a graphml file from the dependencies of the project."
