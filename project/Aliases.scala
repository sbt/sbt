
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.{scriptedConf, scriptedLaunchConf, scriptedSbt, scriptedSettings}

import com.typesafe.sbt.SbtProguard.proguardSettings

object Aliases {

  def libs = libraryDependencies

  def withScriptedTests =
    // see https://github.com/sbt/sbt/issues/3325#issuecomment-315670424
    scriptedSettings.filterNot(_.key.key.label == libraryDependencies.key.label) ++ Seq(
    libraryDependencies ++= {
      CrossVersion.binarySbtVersion(scriptedSbt.value) match {
        case "0.13" =>
          Seq(
            "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
            "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
          )
        case _ =>
          Seq(
            "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % scriptedConf.toString,
            "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % scriptedLaunchConf.toString
          )
      }
    }
  )

  def hasITs = itSettings

  def proguard = proguardSettings

  def ShadingPlugin = coursier.ShadingPlugin

  def root = file(".")


  implicit class ProjectOps(val proj: Project) extends AnyVal {
    def dummy: Project =
      proj.in(file(s"target/${proj.id}"))
  }

}
