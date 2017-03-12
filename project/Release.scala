import sbt._
import Keys._
import _root_.bintray.BintrayPlugin.autoImport._
import _root_.bintray.InternalBintrayKeys._

object Release {
  lazy val launcherRemotePath = SettingKey[String]("launcher-remote-path")
  lazy val deployLauncher = TaskKey[Unit](
    "deploy-launcher",
    "Upload the launcher to its traditional location for compatibility with existing scripts."
  )

  def launcherSettings(launcher: TaskKey[File]): Seq[Setting[_]] = Seq(
    launcherRemotePath := s"${organization.value}/${moduleName.value}/${version.value}/${moduleName.value}.jar",
    deployLauncher := {
      val repo = bintrayRepo.value
      repo.upload(bintrayPackage.value, version.value, launcherRemotePath.value, launcher.value, sLog.value)
    }
  )
}
