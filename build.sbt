sbtPlugin := true

name := "sbt-extras-plugin"

organization := "org.improving"

version <<= (sbtVersion)("0.1.0-%s".format(_))

