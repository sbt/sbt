import sbt._
import Keys._

object TwirlPlugin extends AutoPlugin {
  override def requires = sbt.plugins.JvmPlugin
  override def trigger = noTrigger

  object autoImport {
    val twirlCompileTemplates = taskKey[Seq[File]]("Compile twirl templates into scala source files")
  }

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(twirlSettings) ++
        inConfig(Test)(twirlSettings)

  import autoImport._

  def twirlSettings: Seq[Setting[_]] =  Seq(
    twirlCompileTemplates / includeFilter := "*.scala.*",
    twirlCompileTemplates / excludeFilter := HiddenFileFilter,
    twirlCompileTemplates / sourceDirectories := Seq(sourceDirectory.value / "twirl"),

    twirlCompileTemplates / sources := Defaults.collectFiles(
      twirlCompileTemplates / sourceDirectories,
      twirlCompileTemplates / includeFilter,
      twirlCompileTemplates / excludeFilter
    ).value
  )
}
