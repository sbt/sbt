package resource

import java.net.URL
import java.nio.file._
import java.util.Collections

object Resource {
  def readFile(url: URL): String = {
      val uri = url.toURI
      val fileSystem =
        if (uri.getScheme != "jar") None
        else Some(FileSystems.newFileSystem(uri, Collections.emptyMap[String, Object]))
      try new String(Files.readAllBytes(Paths.get(uri)))
      finally fileSystem.foreach(_.close())
  }
  def getStringResource(name: String): String = {
    readFile(Resource.getClass.getClassLoader.getResource(name))
  }
}
