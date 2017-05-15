package coursier.sbtlauncher

import java.io.File
import java.nio.file.{Files, StandardCopyOption}

import scala.collection.mutable

final class ComponentProvider(cacheDir: File) extends xsbti.ComponentProvider {

  private val components0 = new mutable.HashMap[String, Array[File]]

  def componentLocation(id: String): File =
    new File(cacheDir, id)

  def component(componentId: String): Array[File] = {
    val res = components0.getOrElse[Array[File]](
      componentId,
      {
        val dir = componentLocation(componentId)
        if (dir.exists())
          Option(dir.listFiles()).getOrElse(Array())
        else
          Array()
      }
    )
    res
  }

  private def clear(componentId: String): Unit = {

    def deleteRecursively(f: File): Unit =
      if (f.isFile)
        f.delete()
      else
        Option(f.listFiles())
          .getOrElse(Array())
          .foreach(deleteRecursively)

    val dir = componentLocation(componentId)
    deleteRecursively(dir)
  }

  private def copying(componentId: String, f: File): File = {

    // TODO Use some locking mechanisms here

    val dir = componentLocation(componentId)
    dir.mkdirs()
    val dest = new File(dir, f.getName)
    Files.copy(f.toPath, dest.toPath, StandardCopyOption.REPLACE_EXISTING)
    dest
  }

  def defineComponentNoCopy(componentId: String, components: Array[File]): Unit = {
    components0 += componentId -> components.distinct
  }
  def defineComponent(componentId: String, components: Array[File]): Unit = {
    clear(componentId)
    components0 += componentId -> components.distinct.map(copying(componentId, _))
  }
  def addToComponent(componentId: String, components: Array[File]): Boolean = {
    val previousFiles = components0.getOrElse(componentId, Array.empty[File])
    val newFiles = (previousFiles ++ components.distinct.map(copying(componentId, _))).distinct
    components0 += componentId -> newFiles
    newFiles.length != previousFiles.length
  }

  def lockFile: File = new File("/component-lock")

}
