import sbt._
import Keys._

object BuildExtrasPlugin extends AutoPlugin {
  override def buildExtras: Seq[Project] =
    List("foo", "bar", "baz") map generateProject

  def generateProject(id: String): Project =
    Project(id, file(id)).
      settings(
        name := id
      )
}
