import sbt._, Keys._
import com.typesafe.sbt.SbtScalariform._, autoImport._

object Formatting {
  val BuildConfig = config("build") extend Compile
  val BuildSbtConfig = config("buildsbt") extend Compile

  val scalariformCheck = taskKey[Unit]("Checks that the existing code is formatted, via git diff")

  private val prefs: Seq[Setting[_]] = {
    import scalariform.formatter.preferences._
    Seq(
      scalariformPreferences ~= (_
        .setPreference(AlignSingleLineCaseStatements, true)
        .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
        .setPreference(DanglingCloseParenthesis, Force)
      )
    )
  }

  val settings: Seq[Setting[_]] = Seq() ++ prefs
  val sbtFilesSettings: Seq[Setting[_]] = Seq() ++ prefs ++
    inConfig(BuildConfig)(configScalariformSettings) ++
    inConfig(BuildSbtConfig)(configScalariformSettings) ++
    Seq(
      scalaSource in BuildConfig := baseDirectory.value / "project",
      scalaSource in BuildSbtConfig := baseDirectory.value / "project",
      includeFilter in (BuildConfig, scalariformFormat) := ("*.scala": FileFilter),
      includeFilter in (BuildSbtConfig, scalariformFormat) := ("*.sbt": FileFilter),
      scalariformFormat in Compile := {
        val x = (scalariformFormat in BuildSbtConfig).value
        val y = (scalariformFormat in BuildConfig).value
        (scalariformFormat in Compile).value
      },
      scalariformCheck := {
        val diff = "git diff".!!
        if (diff.nonEmpty) sys.error("Working directory is dirty!\n" + diff)
      }
    )
}
