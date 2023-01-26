import sbt._, Keys._

object ExtraProjectPluginExample extends AutoPlugin {
  override def extraProjects: Seq[Project] =
    List("foo", "bar", "baz") map generateProject

  def generateProject(id: String): Project =
    Project(id, file(id)).
      settings(
        name := id
      )
}
