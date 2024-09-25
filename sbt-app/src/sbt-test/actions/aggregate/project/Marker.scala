import sbt._, Keys._
import Def.Initialize
import sbt.TupleSyntax.*

object Marker extends AutoPlugin:
  override def trigger = allRequirements
  override def requires = sbt.plugins.JvmPlugin
  object autoImport {
    final lazy val mark = taskKey[Unit]("mark")
    final def markTask: Initialize[Task[Unit]] = mark(baseDirectory)
    final def mark(project: Reference): Initialize[Task[Unit]] = mark(project / baseDirectory)
    final def mark(baseKey: SettingKey[File]): Initialize[Task[Unit]] = baseKey.toTaskable mapN {
      base =>
        val toMark = base / "ran"
        if (toMark.exists)
          sys.error("Already ran (" + toMark + " exists)")
        else
          IO touch toMark
    }
  }
end Marker
