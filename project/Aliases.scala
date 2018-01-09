
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._
import sbt.ScriptedPlugin.autoImport._
import sbt.ScriptedPlugin.{projectSettings => scriptedSettings}
import sbt.librarymanagement.CrossVersion.partialVersion

object Aliases {

  def libs = libraryDependencies

  def withScriptedTests = {

    // Both tweaked tasks adapted from the scripted sources.
    // No idea why this is required to run the scripted tests for sbt 0.13.

    def tweakedScriptedRunTask = Def.task {
      // similar to https://github.com/sbt/sbt/issues/3245#issuecomment-306045952
      val cls = scriptedTests.value.getClass

      if (sbtVersion.in(pluginCrossBuild).value.startsWith("0.13."))
        cls.getMethod("run",
          classOf[File],
          classOf[Boolean],
          classOf[Array[String]],
          classOf[File],
          classOf[Array[String]]
        )
      else
        cls.getMethod("run",
          classOf[File],
          classOf[Boolean],
          classOf[Array[String]],
          classOf[File],
          classOf[Array[String]],
          classOf[java.util.List[File]]
        )
    }

    def tweakedScriptedTask = Def.inputTask {
      import scala.language.reflectiveCalls

      val args = ScriptedPlugin
        .asInstanceOf[{
          def scriptedParser(f: File): complete.Parser[Seq[String]]
        }]
        .scriptedParser(sbtTestDirectory.value)
        .parsed

      scriptedDependencies.value

      try {
        if (sbtVersion.in(pluginCrossBuild).value.startsWith("0.13."))
          scriptedRun.value.invoke(
            scriptedTests.value,
            sbtTestDirectory.value,
            scriptedBufferLog.value: java.lang.Boolean,
            args.toArray,
            sbtLauncher.value,
            scriptedLaunchOpts.value.toArray
          )
        else
          scriptedRun.value.invoke(
            scriptedTests.value,
            sbtTestDirectory.value,
            scriptedBufferLog.value: java.lang.Boolean,
            args.toArray,
            sbtLauncher.value,
            scriptedLaunchOpts.value.toArray,
            new java.util.ArrayList()
          )
      } catch {
        case e: java.lang.reflect.InvocationTargetException =>
          throw e.getCause
      }
    }


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
      },
      scriptedRun := tweakedScriptedRunTask.value,
      scripted := tweakedScriptedTask.evaluated
    )
  }

  def hasITs = itSettings

  def ShadingPlugin = coursier.ShadingPlugin

  def root = file(".")


  implicit class ProjectOps(val proj: Project) extends AnyVal {
    def dummy: Project =
      proj.in(file(s"target/${proj.id}"))
  }

}
