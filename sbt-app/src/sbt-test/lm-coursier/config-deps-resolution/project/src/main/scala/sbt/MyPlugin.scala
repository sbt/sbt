package sbt

import sbt._
import Keys._

object MyPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin
  override def trigger = allRequirements

  val FooConfig = config("foo")

  override def projectSettings = Seq[Setting[_]](
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % "1.7.0",
      "ch.qos.logback" % "logback-classic" % "1.1.7"
    ).map(_ % FooConfig),
    ivyConfigurations += FooConfig
  )
}
