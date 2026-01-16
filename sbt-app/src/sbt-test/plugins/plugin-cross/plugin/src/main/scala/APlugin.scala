package example

import sbt._
// import Keys.*

object APlugin extends AutoPlugin {
  object autoImport {
    val a = taskKey[Int]("")
  }

  import autoImport._

  override def projectSettings = Seq(
    a := 2
  )
}
