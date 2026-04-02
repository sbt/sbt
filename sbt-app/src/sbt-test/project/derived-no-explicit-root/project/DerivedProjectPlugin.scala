import sbt._
import sbt.Keys._

object DerivedProjectPlugin extends AutoPlugin {
  val value1 = settingKey[Int]("value1")

  override def derivedProjects(proj: ProjectDefinition[?]) =
    proj.projectOrigin match {
      case ProjectOrigin.DerivedProject =>
        Nil
      case _ =>
        Seq(
          Project("foo", file("foo")).settings(
            value1 := 3,
            name := "foo",
          )
        )
    }

  override def trigger = allRequirements
}
