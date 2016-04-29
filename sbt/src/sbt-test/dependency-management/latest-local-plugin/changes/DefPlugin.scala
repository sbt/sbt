import sbt._
import Keys._

object DefPlugin extends AutoPlugin {
  override val requires = plugins.JvmPlugin
  override val trigger = allRequirements

  object autoImport {
    val aValue = "demo"
  }
}
