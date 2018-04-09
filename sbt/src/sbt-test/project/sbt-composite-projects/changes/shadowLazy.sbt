import sbt.internal.AddSettings
import sbt.CompositeProject

lazy val check = taskKey[Unit]("check")

lazy val fooJS = foo.js.settings(version := "0.2.1") // this one needs to win

// Based on sbt-file-projects test
lazy val foo = new CompositeProject
{
  val jvm = Project.apply("jvm", new File("jvm")).settings(version := "0.1.0")
  val js = Project.apply("js", new File("js")).settings(version := "0.1.0")
  def componentProjects: Seq[Project] = Seq(jvm, js)
}

lazy val fooJVM = foo.jvm.settings(version := "0.2.0") // this one needs to win

lazy val bar = project
  .dependsOn(foo.jvm)

val g = taskKey[Unit]("A task in the root project")
g := println("Hello.")


check := {
  val verJvm = (version in foo.jvm).?.value
  assert (verJvm == Some("0.2.0"))

  val verFooJvm = (version in fooJVM).?.value
  assert (verFooJvm == Some("0.2.0"))

  val verJs = (version in foo.js).?.value
  assert (verJs == Some("0.2.1"))

  val verFooJs = (version in fooJS).?.value
  assert (verFooJs == Some("0.2.1"))
}
