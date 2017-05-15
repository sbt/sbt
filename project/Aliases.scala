
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.scriptedSettings

import com.typesafe.sbt.SbtProguard.proguardSettings

object Aliases {

  def libs = libraryDependencies

  def withScriptedTests = scriptedSettings

  def hasITs = itSettings

  def proguard = proguardSettings

  def ShadingPlugin = coursier.ShadingPlugin


  implicit class ProjectOps(val proj: Project) extends AnyVal {
    def dummy: Project =
      proj.in(file(s"target/${proj.id}"))
  }

}
