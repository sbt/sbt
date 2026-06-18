import sbt._
import sbt.Keys._

object PluginCrossSbtVersionOverridePlugin extends AutoPlugin {
  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.12.12"
        case _      => sbtVersion.value
      }
    }
  )
}

object ConcreteSbtPlugin extends AutoPlugin {
  override def requires = PluginCrossSbtVersionOverridePlugin && plugins.SbtPlugin
}

object SameAsRunningPlugin extends AutoPlugin {
  override def requires = plugins.SbtPlugin

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    pluginCrossBuild / sbtVersion := sbtVersion.value
  )
}
