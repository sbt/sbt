package coursier.sbtlauncher

import java.io.File

import scala.language.existentials

final case class AppProvider(
  scalaProvider: xsbti.ScalaProvider,
  id: xsbti.ApplicationID,
  loader: ClassLoader,
  mainClass: Class[_ <: xsbti.AppMain],
  createMain: () => xsbti.AppMain,
  mainClasspath: Array[File],
  components: xsbti.ComponentProvider
) extends xsbti.AppProvider {
  def entryPoint: Class[_] =
    mainClass
  def newMain(): xsbti.AppMain =
    createMain()
}
