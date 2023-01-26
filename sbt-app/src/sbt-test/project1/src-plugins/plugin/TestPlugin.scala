import sbt._

object TestPlugin extends AutoPlugin {
  override val requires = plugins.JvmPlugin
  override val trigger = allRequirements

  object autoImport {
    val Check = TaskKey[Unit]("check")
  }
  import autoImport._

  override def projectSettings = Seq(
    Check := assert(JavaTest.X == 9)
  )
}
