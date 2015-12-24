package sbt
package internal
package inc

import java.io.File
import sbt.util.Logger
import sbt.internal.scripted.StatementHandler
import sbt.util.InterfaceUtil._
import xsbt.api.Discovery
import xsbti.{ F1, Maybe }
import xsbti.compile.{ CompileAnalysis, CompileOrder, DefinesClass, IncOptionsUtil, PreviousResult, Compilers => XCompilers }
import sbt.io.IO
import sbt.io.Path._

import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ isPublic, isStatic }
import sbt.internal.inc.classpath.ClasspathUtilities

import sbt.internal.scripted.{ StatementHandler, TestFailed }

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
  def unmanagedJars: List[File] = (directory / "lib" ** "*.jar").get.toList

  lazy val commands: Map[String, IncCommand] = Map(
    "compile" -> {
      case (Nil, i) =>
        compile(i)
        ()
      case (xs, _) => wrongArguments("compile", xs)
    },
    "clean" -> {
      case (Nil, i) =>
        clean(i)
        ()
      case (xs, _) => wrongArguments("clean", xs)
    },
    "checkIterations" -> {
      case (x :: Nil, i) =>
        checkNumberOfCompilerIterations(i, x.toInt)
      case (xs, _) => wrongArguments("checkIterations", xs)
    },
    "checkSame" -> {
      case (Nil, i) =>
        checkSame(i)
        ()
      case (xs, _) => wrongArguments("checkSame", xs)
    },
    "run" -> {
      case (params, i) =>
        val analysis = compile(i)
        discoverMainClasses(analysis) match {
          case Seq(mainClassName) =>
            val classpath = i.si.allJars :+ classesDir
            val loader = ClasspathUtilities.makeLoader(classpath, i.si, directory)
            val main = getMainMethod(mainClassName, loader)
            invokeMain(loader, main, params)
          case _ =>
            throw new TestFailed("Found more than one main class.")
        }
    }
  )

  def checkSame(i: IncInstance): Unit =
    {
      val analysis = compile(i)
      analysis.apis.internal foreach {
        case (_, api) =>
          assert(xsbt.api.SameAPI(api.api, api.api))
      }
    }

  def clean(i: IncInstance): Unit =
    IO.delete(classesDir)

  def checkNumberOfCompilerIterations(i: IncInstance, expected: Int): Unit =
    {
      val analysis = compile(i)
      assert(
        (analysis.compilations.allCompilations.size: Int) == expected,
        "analysis.compilations.allCompilations.size = %d (expected %d)".format(analysis.compilations.allCompilations.size, expected)
      )
    }

  def compile(i: IncInstance): Analysis =
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
      val classpath = (si.allJars.toList ++ unmanagedJars).toArray
      val in = compiler.inputs(classpath, scalaSources.toArray, classesDir, Array(), Array(), maxErrors, Array(),
        CompileOrder.Mixed, cs, setup, prev)
      val result = compiler.compile(in, log)
      val analysis = result.analysis match { case a: Analysis => a }
      fileStore.set(analysis, result.setup)
      analysis
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

  // Taken from Defaults.scala in sbt/sbt
  private def discoverMainClasses(analysis: inc.Analysis): Seq[String] = {
    val allDefs = analysis.apis.internal.values.flatMap(_.api.definitions).toSeq
    Discovery.applications(allDefs).collect({ case (definition, discovered) if discovered.hasMain => definition.name }).sorted
  }

  // Taken from Run.scala in sbt/sbt
  private def getMainMethod(mainClassName: String, loader: ClassLoader) =
    {
      val mainClass = Class.forName(mainClassName, true, loader)
      val method = mainClass.getMethod("main", classOf[Array[String]])
      // jvm allows the actual main class to be non-public and to run a method in the non-public class,
      //  we need to make it accessible
      method.setAccessible(true)
      val modifiers = method.getModifiers
      if (!isPublic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not public")
      if (!isStatic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not static")
      method
    }

  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]); () }
    finally { currentThread.setContextClassLoader(oldLoader) }
  }
}
