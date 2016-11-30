import sbt._

object FooPlugin extends AutoPlugin {
  override def trigger = noTrigger
  object autoImport {
    val myTask = taskKey[Unit]("My task")
  }

  import autoImport._

  override def buildSettings = super.buildSettings ++ Seq(
    myTask := println("Called my task")
  )
}
