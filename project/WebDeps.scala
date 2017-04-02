
import sbt._
import sbt.Keys._

object WebDeps {
  def bootstrap = "org.webjars.bower" % "bootstrap" % "3.3.4"
  def react = "org.webjars.bower" % "react" % "0.12.2"
  def bootstrapTreeView = "org.webjars.bower" % "bootstrap-treeview" % "1.2.0"
  def raphael = "org.webjars.bower" % "raphael" % "2.1.4"
}
