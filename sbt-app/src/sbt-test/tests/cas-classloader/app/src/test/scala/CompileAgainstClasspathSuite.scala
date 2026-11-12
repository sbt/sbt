import java.io.File
import java.net.URLClassLoader

import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualDirectory
import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.reporters.StoreReporter

class CompileAgainstClasspathSuite extends munit.FunSuite {
  private val classpath = Iterator
    .iterate(getClass.getClassLoader)(_.getParent)
    .takeWhile(_ != null)
    .collect { case loader: URLClassLoader => loader.getURLs }
    .flatten
    .map(url => new File(url.toURI))
    .toList

  test("the loader exposes the core archive with its original name") {
    assert(classpath.exists(_.getName == "core_2.13.jar"), classpath.mkString("\n"))
    val rawBlobs = classpath.filter(entry => Option(entry.toPath.getParent).exists(_.endsWith("diskcache/cas")))
    assert(rawBlobs.isEmpty, rawBlobs.mkString("\n"))
  }

  test("scalac can compile against the loader classpath") {
    val settings = new Settings
    settings.classpath.value = classpath.mkString(File.pathSeparator)
    settings.outputDirs.setSingleOutput(new VirtualDirectory("out", None))
    val reporter = new StoreReporter(settings)
    val compiler = new Global(settings, reporter)
    new compiler.Run().compileSources(
      List(new BatchSourceFile("Snippet.scala", "object Snippet { val x = core.Greeter.hello }"))
    )
    assert(!reporter.hasErrors, reporter.infos.map(_.msg).mkString("\n"))
  }
}
