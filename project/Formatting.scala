import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform.{ ScalariformKeys => sr, _ }

object Formatting {
  lazy val BuildConfig = config("build") extend Compile
  lazy val BuildSbtConfig = config("buildsbt") extend Compile

  lazy val settings: Seq[Setting[_]] = Seq() ++ scalariformSettings ++ prefs
  lazy val prefs: Seq[Setting[_]] = {
    import scalariform.formatter.preferences._
    Seq(
      sr.preferences := sr.preferences.value.setPreference(AlignSingleLineCaseStatements, true)
    )
  }
  lazy val sbtFilesSettings: Seq[Setting[_]] = Seq() ++ scalariformSettings ++ prefs ++
    inConfig(BuildConfig)(configScalariformSettings) ++
    inConfig(BuildSbtConfig)(configScalariformSettings) ++
    Seq(
      scalaSource in BuildConfig := baseDirectory.value / "project",
      scalaSource in BuildSbtConfig := baseDirectory.value / "project",
      includeFilter in (BuildConfig, sr.format) := ("*.scala": FileFilter),
      includeFilter in (BuildSbtConfig, sr.format) := ("*.sbt": FileFilter),
      sr.format in Compile := {
        val x = (sr.format in BuildSbtConfig).value
        val y = (sr.format in BuildConfig).value
        (sr.format in Compile).value
      }
    )
}
