package myplugin

import sbt._
import sbt.Keys._

case object MyPlugin extends AutoPlugin {
  object autoImport {
    val helloWorld = Def.taskKey[String]("log and return hello world")
  }
  import autoImport._
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    // should not produce a "@nowarn annotation does not suppres any warnings" warning
    helloWorld := {
      streams.value.log("Hello world")
      "Hello world"
    },
    Compile / compile := {
      helloWorld.value // shoult not produce "a pure expression does nothing" warning
      (Compile / compile).value
    }
  )
}
