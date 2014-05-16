import sbt._
import Keys._

object EmptyLoaderBuild extends Build {
  override def buildLoaders = BuildLoader.transform(_.unit) :: Nil
  lazy val root = (
    project in file(".")
    settings(
      name := "foo"
    )
  )
}