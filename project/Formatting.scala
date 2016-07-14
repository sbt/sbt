import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtScalariform._
import ScalariformKeys.{ format => scalariformFormat, preferences => scalariformPreferences }

object Formatting {
  lazy val BuildConfig = config("build") extend Compile
  lazy val BuildSbtConfig = config("buildsbt") extend Compile

  val scalariformCheck = taskKey[Unit]("Checks that the existing code is formatted, via git diff")

  lazy val settings: Seq[Setting[_]] = Seq() ++ scalariformSettings ++ prefs
  lazy val prefs: Seq[Setting[_]] = {
    import scalariform.formatter.preferences._
    Seq(
      scalariformPreferences ~= (_.setPreference(AlignSingleLineCaseStatements, true))
    )
  }
  lazy val sbtFilesSettings: Seq[Setting[_]] = Seq() ++ scalariformSettings ++ prefs ++
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
