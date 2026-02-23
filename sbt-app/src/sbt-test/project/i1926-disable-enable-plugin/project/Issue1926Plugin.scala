import sbt._

object Issue1926Plugin extends AutoPlugin {
  object autoImport {
    val issue1926Marker = settingKey[String]("Marker setting for issue #1926 test")
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger = noTrigger
  override def projectSettings = Seq(issue1926Marker := "enabled")
}
