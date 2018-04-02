import sbt.internal.AddSettings
import sbt.CompositeProject

lazy val check = taskKey[Unit]("check")

lazy val a = (project in file("a"))
  .settings(
    version := "0.2.0"
  )

// Based on sbt-file-projects test
lazy val cross = new CompositeProject
{
  val p1 = Project.apply("a", new File("a"))
  val p2 = Project.apply("b", new File("b"))
  def componentProjects: Seq[Project] = Seq(p1, p2)
}

lazy val b = (project in file("b"))
  .settings(
    version := "0.2.0"
  )

val g = taskKey[Unit]("A task in the root project")
g := println("Hello.")


check := {
  val verP1 = (version in cross.p1).?.value
  assert (verP1 == Some("0.2.0"))//Some("0.1.0-SNAPSHOT"))

  val verP2 = (version in cross.p2).?.value
  assert (verP2 == Some("0.1.0-SNAPSHOT"))

  val verA = (version in a).?.value
  assert (verA == Some("0.2.0"))

  val verB = (version in b).?.value
  assert (verA == Some("0.2.0"))
}
