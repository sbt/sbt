package coursier.util

// Extracted and adapted from SBT

import java.io.File
import java.net._

/**
 * Doesn't load any classes itself, but instead verifies that all classes loaded through `parent`
 * come from `classpath`.
 *
 * If `exclude` is `true`, does the opposite - exclude classes from `classpath`.
 */
class ClasspathFilter(parent: ClassLoader, classpath: Set[File], exclude: Boolean) extends ClassLoader(parent) {
  override def toString = s"ClasspathFilter(parent = $parent, cp = $classpath)"

  private val directories = classpath.toSeq.filter(_.isDirectory)


  private def toFile(url: URL) =
    try { new File(url.toURI) }
    catch { case _: URISyntaxException => new File(url.getPath) }

  private def uriToFile(uriString: String) = {
    val uri = new URI(uriString)
    assert(uri.getScheme == "file", s"Expected protocol to be 'file' in URI $uri")
    if (uri.getAuthority == null)
      new File(uri)
    else {
      /* https://github.com/sbt/sbt/issues/564
       * http://blogs.msdn.com/b/ie/archive/2006/12/06/file-uris-in-windows.aspx
       * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5086147
       * The specific problem here is that `uri` will have a defined authority component for UNC names like //foo/bar/some/path.jar
       * but the File constructor requires URIs with an undefined authority component.
       */
      new File(uri.getSchemeSpecificPart)
    }
  }

  private def urlAsFile(url: URL) =
    url.getProtocol match {
      case "file" => Some(toFile(url))
      case "jar" =>
        val path = url.getPath
        val end = path.indexOf('!')
        Some(uriToFile(if (end < 0) path else path.substring(0, end)))
      case _ => None
    }

  private def baseFileString(baseFile: File) =
    Some(baseFile).filter(_.isDirectory).map { d =>
      val cp = d.getAbsolutePath.ensuring(_.nonEmpty)
      if (cp.last == File.separatorChar) cp else cp + File.separatorChar
    }

  private def relativize(base: File, file: File) =
    baseFileString(base) .flatMap { baseString =>
      Some(file.getAbsolutePath).filter(_ startsWith baseString).map(_ substring baseString.length)
    }

  private def fromClasspath(c: Class[_]): Boolean = {
    val codeSource = c.getProtectionDomain.getCodeSource
    (codeSource eq null) ||
      onClasspath(codeSource.getLocation) ||
      // workaround SBT classloader returning the target directory as sourcecode
      // make us keep more class than expected
      urlAsFile(codeSource.getLocation).exists(_.isDirectory)
  }

  private val onClasspath: URL => Boolean = {
    if (exclude)
      src =>
        src == null || urlAsFile(src).forall(f =>
          !classpath(f) &&
          directories.forall(relativize(_, f).isEmpty)
        )
    else
      src =>
        src == null || urlAsFile(src).exists(f =>
          classpath(f) ||
          directories.exists(relativize(_, f).isDefined)
        )
  }


  override def loadClass(className: String, resolve: Boolean): Class[_] = {
    val c = super.loadClass(className, resolve)
    if (fromClasspath(c)) c
    else throw new ClassNotFoundException(className)
  }

  override def getResource(name: String): URL = {
    val res = super.getResource(name)
    if (onClasspath(res)) res else null
  }

  override def getResources(name: String): java.util.Enumeration[URL] = {
    import collection.JavaConverters._
    val res = super.getResources(name)
    if (res == null) null else res.asScala.filter(onClasspath).asJavaEnumeration
  }
}
