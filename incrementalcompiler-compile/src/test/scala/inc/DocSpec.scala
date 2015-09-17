package sbt
package inc

import java.io.File

import sbt.io.IO
import sbt.util.Logger
import sbt.internal.util.UnitSpec
import sbt.internal.inc.javac.{ JavaTools, JavaCompiler, Javadoc }
import sbt.internal.inc.javac.JavaCompilerSpec
import sbt.internal.inc.LoggerReporter
// import org.scalatest.matchers._

class DocSpec extends UnitSpec {
  "Doc.cachedJavadoc" should "generate Java Doc" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", cacheDir, local)
        javadoc.run(List(knownSampleGoodFile), Nil, out, Nil, log, reporter)
        assert((new File(out, "index.html")).exists)
        assert((new File(out, "good.html")).exists)
      }
    }
  }
  it should "generate cache input" in {
    IO.withTemporaryDirectory { cacheDir =>
      IO.withTemporaryDirectory { out =>
        val javadoc = Doc.cachedJavadoc("Foo", cacheDir, local)
        javadoc.run(List(knownSampleGoodFile), Nil, out, Nil, log, reporter)
        assert((new File(cacheDir, "inputs")).exists)
      }
    }
  }

  def local =
    JavaTools(
      JavaCompiler.local.getOrElse(sys.error("This test cannot be run on a JRE, but only a JDK.")),
      Javadoc.local.getOrElse(Javadoc.fork())
    )
  lazy val log = Logger.Null
  lazy val reporter = new LoggerReporter(10, log)
  def knownSampleGoodFile =
    new File(classOf[JavaCompilerSpec].getResource("good.java").toURI)
}
