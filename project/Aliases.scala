
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._
import sbt.librarymanagement.CrossVersion.partialVersion

object Aliases {

  def withScriptedTests: Seq[Def.Setting[_]] =
    ScriptedPlugin.globalSettings ++ ScriptedPlugin.projectSettings.filterNot(_.key.key.label == libraryDependencies.key.label) ++ Seq(
      libraryDependencies ++= Seq(
        "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % ScriptedConf,
        "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % ScriptedLaunchConf
      )
    )

}
