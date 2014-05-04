package sbt.compiler

import java.io.File
import sbt.{ CompileSetup, IO, Using }
import sbt.inc.{ Analysis, IncOptions, TextAnalysisFormat }
import xsbti.{ Logger, Maybe }
import xsbti.compile._

object IC extends IncrementalCompiler[Analysis, AnalyzingCompiler] {
  def compile(in: Inputs[Analysis, AnalyzingCompiler], log: Logger): Analysis =
    {
      val setup = in.setup; import setup._
      val options = in.options; import options.{ options => scalacOptions, _ }
      val compilers = in.compilers; import compilers._
      val agg = new AggressiveCompile(setup.cacheFile)
      val aMap = (f: File) => m2o(analysisMap(f))
      val defClass = (f: File) => { val dc = definesClass(f); (name: String) => dc.apply(name) }
      val incOptions = IncOptions.fromStringMap(incrementalCompilerOptions)
      agg(scalac, javac, sources, classpath, output, cache, m2o(progress), scalacOptions, javacOptions, aMap,
        defClass, reporter, order, skip, incOptions)(log)
    }

  private[this] def m2o[S](opt: Maybe[S]): Option[S] = if (opt.isEmpty) None else Some(opt.get)

  def newScalaCompiler(instance: ScalaInstance, interfaceJar: File, options: ClasspathOptions, log: Logger): AnalyzingCompiler =
    new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(interfaceJar), options, log)

  def compileInterfaceJar(label: String, sourceJar: File, targetJar: File, interfaceJar: File, instance: ScalaInstance, log: Logger) {
    val raw = new RawCompiler(instance, sbt.ClasspathOptions.auto, log)
    AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, interfaceJar :: Nil, label, raw, log)
  }

  def readCache(file: File): Maybe[(Analysis, CompileSetup)] =
    try { Maybe.just(readCacheUncaught(file)) } catch { case _: Exception => Maybe.nothing() }

  @deprecated("Use overloaded variant which takes `IncOptions` as parameter.", "0.13.2")
  def readAnalysis(file: File): Analysis =
    try { readCacheUncaught(file)._1 } catch { case _: Exception => Analysis.Empty }

  def readAnalysis(file: File, incOptions: IncOptions): Analysis =
    try { readCacheUncaught(file)._1 } catch {
      case _: Exception => Analysis.empty(nameHashing = incOptions.nameHashing)
    }

  def readCacheUncaught(file: File): (Analysis, CompileSetup) =
    Using.fileReader(IO.utf8)(file) { reader =>
      try {
        TextAnalysisFormat.read(reader)
      } catch {
        case ex: sbt.inc.ReadException =>
          throw new java.io.IOException(s"Error while reading $file", ex)
      }
    }
}
