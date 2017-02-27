package coursier.sbtlauncher

import java.io.File

final case class ScalaProvider(
  launcher: xsbti.Launcher,
  version: String,
  loader: ClassLoader,
  jars: Array[File],
  libraryJar: File,
  compilerJar: File,
  createApp: xsbti.ApplicationID => xsbti.AppProvider
) extends xsbti.ScalaProvider {
  def app(id: xsbti.ApplicationID): xsbti.AppProvider =
    createApp(id)
}
