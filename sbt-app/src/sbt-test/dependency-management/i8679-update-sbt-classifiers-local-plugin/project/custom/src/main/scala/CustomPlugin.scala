import sbt.*
import sbt.Keys.*

object CustomPlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val customPluginCheck = taskKey[String]("A task provided by the custom plugin")
  }

  import autoImport.*

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    customPluginCheck := {
      import cats.implicits.*
      List("custom", " ", "plugin").combineAll
    }
  )
}
