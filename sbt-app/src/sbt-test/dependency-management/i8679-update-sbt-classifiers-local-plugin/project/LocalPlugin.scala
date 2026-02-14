import sbt.*
import sbt.Keys.*

object LocalPlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val localPluginCheck = taskKey[String]("A task provided by the local plugin")
  }

  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    localPluginCheck := "local-plugin-active"
  )
}
