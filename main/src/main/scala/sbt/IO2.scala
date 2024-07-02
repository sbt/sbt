package sbt

import java.io.File
import java.net.URI
import java.nio.file.FileSystemNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

// TODO remove and use IO.unzip as soon as Zinc can create regular Jar files
object IO2 {
  def unzip(from: File, toDirectory: File): Set[File] =
    val dirPath = toDirectory.toPath
    val set = mutable.Set.empty[File]
    jarFileSystem(from) { root =>
      Files
        .walk(root)
        .iterator
        .asScala
        .foreach { entry =>
          val target = dirPath.resolve(root.relativize(entry).toString)
          if !Files.isDirectory(target) then Files.copy(entry, target)
          if Files.isRegularFile(target) then set += target.toFile
        }
    }
    set.toSet

  def jarFileSystem(file: File)(f: Path => Unit): Unit =
    val uri = URI.create(s"jar:${file.toURI}")
    val fs =
      try FileSystems.getFileSystem(uri)
      catch
        case _: FileSystemNotFoundException =>
          FileSystems.newFileSystem(uri, new java.util.HashMap[String, Any])
    f(fs.getPath("/"))
    // finally fs.close
}
