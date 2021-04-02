import sbt._, Keys._
import Def.Initialize

object Marker extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = sbt.plugins.JvmPlugin
  object autoImport {
    final lazy val Mark = TaskKey[Unit]("mark")
    final def mark: Initialize[Task[Unit]] = mark(baseDirectory)
    final def mark(project: Reference): Initialize[Task[Unit]] = mark(baseDirectory in project)
    final def mark(baseKey: SettingKey[File]): Initialize[Task[Unit]] = baseKey map { base =>
      val toMark = base / "ran"
      if(toMark.exists)
        sys.error("Already ran (" + toMark + " exists)")
      else
        IO touch toMark
    }
  }
}
