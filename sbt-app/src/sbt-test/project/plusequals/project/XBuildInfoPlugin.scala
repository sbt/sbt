import sbt._
import Keys._

object XBuildInfoPlugin extends sbt.AutoPlugin {
  override def requires = plugins.JvmPlugin

  object autoImport {
    lazy val buildInfoKeys = settingKey[Seq[BuildInfoKey.Entry[_]]]("Entries for build info.")
  }
  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    buildInfoKeys := Seq(name, version, scalaVersion, sbtVersion),
  )
}

object BuildInfoKey {
  implicit def setting[A](key: SettingKey[A]): Entry[A] = Setting(key)
  implicit def task[A](key: TaskKey[A]): Entry[A] = Task(key)
  def apply[A](key: SettingKey[A]): Entry[A] = Setting(key)
  def apply[A](key: TaskKey[A]): Entry[A] = Task(key)

  case class Setting[A](scoped: SettingKey[A]) extends Entry[A] {
    def manifest = scoped.key.manifest
  }
  case class Task[A](scoped: TaskKey[A]) extends Entry[A] {
    def manifest = scoped.key.manifest.typeArguments.head.asInstanceOf[Manifest[A]]
  }
  sealed trait Entry[A] {
    def manifest: Manifest[A]
  }
}
