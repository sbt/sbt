import sbt._
import Keys._

object P extends AutoPlugin {
  override val requires = plugins.JvmPlugin
  override val trigger = allRequirements

  override def projectSettings = Seq(
    maxErrors ~= (x => x*x)
  )
}
