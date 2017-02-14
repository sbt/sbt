import sbt._, Keys._

object DatabasePlugin extends AutoPlugin {
  override def requires: Plugins = sbt.plugins.JvmPlugin
  override def trigger = noTrigger

  object autoImport {
    lazy val databaseName = settingKey[String]("name of the database")
  }
  import autoImport._
  override def projectSettings: Seq[Setting[_]] =
    Seq(
      databaseName := "something"
    )
}
