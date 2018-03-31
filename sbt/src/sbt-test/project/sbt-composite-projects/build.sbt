import sbt.internal.AddSettings
import sbt.CompositeProject

// Based on sbt-file-projects test
val cross = new CompositeProject
{
  val p1 = Project.apply("a", new File("a"))
  val p2 = Project.apply("b", new File("b"))
  def componentProjects: Seq[Project] = Seq(p1, p2)
}

val g = taskKey[Unit]("A task in the root project")
g := println("Hello.")
