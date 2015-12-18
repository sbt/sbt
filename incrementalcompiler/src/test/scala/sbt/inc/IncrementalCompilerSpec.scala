package sbt
package inc

import java.io.File

import sbt.internal.inc._
import sbt.io.IO
import sbt.io.Path._
import sbt.util.{ Logger, InterfaceUtil }
import xsbti.{ F1, Maybe }
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil }

class IncrementalCompilerSpec extends BridgeProviderSpecification {

  val scalaVersion = scala.util.Properties.versionNumberString
  val compiler = new IncrementalCompilerImpl // IncrementalCompilerUtil.defaultIncrementalCompiler
  val maxErrors = 100
  val knownSampleGoodFile =
    new File(classOf[IncrementalCompilerSpec].getResource("Good.scala").toURI)

  "incremental compiler" should "compile" in {
    IO.withTemporaryDirectory { tempDir =>
      val compilerBridge = getCompilerBridge(tempDir, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptions.boot, None, sc)
      val analysisMap = f1((f: File) => Maybe.nothing[CompileAnalysis])
      val dc = f1[File, DefinesClass](f => new DefinesClass {
        override def apply(className: String): Boolean = false
      })
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, log, identity)
      val extra = Array(InterfaceUtil.t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, tempDir / "inc_compile", CompilerCache.fresh, incOptions, reporter, extra)
      val prev = compiler.emptyPreviousResult
      val classesDir = tempDir / "classes"
      val in = compiler.inputs(si.allJars, Array(knownSampleGoodFile), classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev)
      compiler.compile(in, log)
      val expectedOuts = List(classesDir / "test" / "pkg" / "Good$.class")
      expectedOuts foreach { f => assert(f.exists, s"$f does not exist.") }
    }
  }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptions.boot)

  def f1[A, B](f: A => B): F1[A, B] =
    new F1[A, B] {
      def apply(a: A): B = f(a)
    }
}
