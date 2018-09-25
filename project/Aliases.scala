
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._
import sbt.librarymanagement.CrossVersion.partialVersion

object Aliases {

  def libs = libraryDependencies

  def withScriptedTests: Seq[Def.Setting[_]] =
    ScriptedPlugin.globalSettings ++ ScriptedPlugin.projectSettings.filterNot(_.key.key.label == libraryDependencies.key.label) ++ Seq(
      libraryDependencies ++= {
        scalaBinaryVersion.value match {
          case "2.12" =>
            partialVersion(scriptedSbt.value) match {
              case Some((1, _)) =>
                Seq(
                  "org.scala-sbt" %% "scripted-sbt" % scriptedSbt.value % ScriptedConf,
                  "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % ScriptedLaunchConf
                )
              case other =>
                sys.error(s"Unrecognized sbt partial version: $other")
            }
          case _ =>
            Seq()
        }
      }
    )

  def hasITs = itSettings

  def ShadingPlugin = coursier.ShadingPlugin

  def root = file(".")


  implicit class ProjectOps(val proj: Project) extends AnyVal {
    def dummy: Project =
      proj.in(file(s"target/${proj.id}"))
  }

}
