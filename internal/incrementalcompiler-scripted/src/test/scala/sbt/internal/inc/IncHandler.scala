package sbt
package internal
package inc

import java.io.File
import sbt.util.Logger
import sbt.internal.scripted.StatementHandler
import sbt.util.InterfaceUtil._
import xsbti.{ F1, Maybe }
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, Compilers => XCompilers }
import sbt.io.IO
import sbt.io.Path._

final case class IncInstance(si: ScalaInstance, cs: XCompilers)

final class IncHandler(directory: File, scriptedLog: Logger) extends BridgeProviderSpecification with StatementHandler {
  type State = Option[IncInstance]
  type IncCommand = (List[String], IncInstance) => Unit
  val compiler = new IncrementalCompilerImpl
  val scalaVersion = scala.util.Properties.versionNumberString
  val maxErrors = 100
  val dc = f1[File, DefinesClass] { f =>
    val x = Locate.definesClass(f)
    new DefinesClass {
      override def apply(className: String): Boolean = x(className)
    }
  }
  val classesDir = directory / "target" / "classes"
  val sourceDirectory = directory / "src" / "main" / "scala"
  def scalaSources: List[File] =
    (sourceDirectory ** "*.scala").get.toList ++
      (directory * "*.scala").get.toList
  val cacheFile = directory / "target" / "inc_compile"
  val fileStore = AnalysisStore.cached(FileBasedStore(cacheFile))

  lazy val commands: Map[String, IncCommand] = Map(
    "compile" -> {
      case (Nil, i) => compile(i)
      case (xs, _)  => wrongArguments("compile", xs)
    }
  )

  def compile(i: IncInstance): Unit =
    {
      import i._
      val sources = scalaSources
      val prev = fileStore.get match {
        case Some((a, s)) => new PreviousResult(Maybe.just(a), Maybe.just(s))
        case _            => compiler.emptyPreviousResult
      }
      val analysisMap = f1((f: File) => prev.analysis)
      val incOptions = IncOptionsUtil.defaultIncOptions()
      val reporter = new LoggerReporter(maxErrors, scriptedLog, identity)
      val extra = Array(t2(("key", "value")))
      val setup = compiler.setup(analysisMap, dc, skip = false, cacheFile, CompilerCache.fresh, incOptions, reporter, extra)
      val in = compiler.inputs(si.allJars, scalaSources.toArray, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev)
      val result = compiler.compile(in, log)
      fileStore.set(result.analysis match { case a: Analysis => a }, result.setup)
    }

  def initialState: State = None

  def apply(command: String, arguments: List[String], i: Option[IncInstance]): Option[IncInstance] =
    onIncInstance(i) { x: IncInstance =>
      commands(command)(arguments, x)
    }

  def onIncInstance(i: Option[IncInstance])(f: IncInstance => Unit): Option[IncInstance] =
    i match {
      case Some(x) =>
        f(x)
        i
      case None =>
        onNewIncInstance(f)
    }

  private[this] def onNewIncInstance(f: IncInstance => Unit): Option[IncInstance] =
    {
      val compilerBridge = getCompilerBridge(directory, Logger.Null, scalaVersion)
      val si = scalaInstance(scalaVersion)
      val sc = scalaCompiler(si, compilerBridge)
      val cs = compiler.compilers(si, ClasspathOptions.boot, None, sc)
      val i = IncInstance(si, cs)
      f(i)
      Some(i)
    }

  def scalaCompiler(instance: ScalaInstance, bridgeJar: File): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(bridgeJar), ClasspathOptions.boot)

  def finish(state: Option[IncInstance]): Unit = ()

  def wrongArguments(commandName: String, args: List[String]): Unit =
    scriptError("Command '" + commandName + "' does not accept arguments (found '" + spaced(args) + "').")

  def spaced[T](l: Seq[T]): String = l.mkString(" ")

  def scriptError(message: String): Unit = sys.error("Test script error: " + message)
}
