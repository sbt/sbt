import sbt._

object GlobalLegacyPlugin extends sbt.Plugin {
  import sbt.Keys._
  val globalLegacyPluginSetting = SettingKey[String]("A top level setting declared by a legacy plugin")
  val useGlobalLegacyPluginSetting = globalLegacyPluginSetting in Global
}
