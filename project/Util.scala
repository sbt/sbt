import sbt._, Keys._

object Util {
  val javaOnlySettings = Seq[Setting[_]](
    crossPaths := false,
    compileOrder := CompileOrder.JavaThenScala,
    unmanagedSourceDirectories in Compile := Seq((javaSource in Compile).value),
    crossScalaVersions := Seq(Dependencies.scala211),
    autoScalaLibrary := false
  )
}
