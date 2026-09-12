import sbt.*
import sbt.Keys.*

// The settings tree below never changes; only the OptsHelper implementation does,
// mimicking a plugin dependency whose new version contributes different scalacOptions.
object TogglePlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def projectSettings = Seq(
    scalacOptions ++= OptsHelper.opts
  )
}
