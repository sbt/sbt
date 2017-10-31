
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport.{ScriptedConf, ScriptedLaunchConf, scriptedSbt}
import sbt.ScriptedPlugin.{projectSettings => scriptedSettings}
import sbt.librarymanagement.CrossVersion.partialVersion

object Aliases {

  def libs = libraryDependencies

  def withScriptedTests =
    // see https://github.com/sbt/sbt/issues/3325#issuecomment-315670424
    scriptedSettings.filterNot(_.key.key.label == libraryDependencies.key.label) ++ Seq(
      libraryDependencies ++= {
        scalaBinaryVersion.value match {
          case "2.10" | "2.12" =>
            partialVersion(scriptedSbt.value) match {
              case Some((0, 13)) =>
                Seq(
                  "org.scala-sbt" % "scripted-sbt" % scriptedSbt.value % ScriptedConf,
                  "org.scala-sbt" % "sbt-launch" % scriptedSbt.value % ScriptedLaunchConf
                )
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
