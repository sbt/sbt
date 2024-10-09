import java.io.File
import java.nio.file.Files

import scala.collection.JavaConverters._

object Main extends App {

  val cp = new collection.mutable.ArrayBuffer[File]

  def buildCp(loader: ClassLoader): Unit =
    if (loader != null) {
      loader match {
        case u: java.net.URLClassLoader =>
          cp ++= u.getURLs
            .map(_.toURI)
            .map(new File(_))
        case _ =>
      }

      buildCp(loader.getParent)
    }

  buildCp(Thread.currentThread().getContextClassLoader)

  System.err.println("Classpath:")
  for (f <- cp)
    System.err.println(s"  $f")
  System.err.println()

  val sbtBase = new File(sys.props.getOrElse(
    "sbt.global.base",
    sys.props("user.home") + "/.sbt"
  ))
  val prefixes = Seq(new File(sbtBase, "boot").getAbsolutePath) ++
    Seq("coursier.sbt-launcher.dirs.scala-jars", "coursier.sbt-launcher.dirs.base", "user.dir")
      .flatMap(sys.props.get(_))
      .map(new File(_).getAbsolutePath)
  val home = new File(sys.props("user.home")).getAbsolutePath

  def notFromCoursierCache(name: String): Unit = {
    val jars = cp.filter(_.getName.startsWith(name)).distinct
    assert(jars.nonEmpty, s"Found no JARs for $name")

    for (jar <- jars)
      assert(
        !jar.getAbsolutePath.startsWith(home) ||
          !jar.getAbsolutePath.toLowerCase(java.util.Locale.ROOT).contains("coursier") ||
          prefixes.exists(jar.getAbsolutePath.startsWith),
        s"JAR for $name ($jar) under $home and not under any of ${prefixes.mkString(", ")}"
      )
  }

  val props = Thread.currentThread()
    .getContextClassLoader
    .getResources("library.properties")
    .asScala
    .toVector
    .map(_.toString)
    .sorted

  notFromCoursierCache("scala-library")
  assert(props.lengthCompare(1) == 0, s"Found several library.properties files in classpath: $props")

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
