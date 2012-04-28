package sbt.compiler

	import sbt.inc.Analysis
	import xsbti.{Logger, Maybe}
	import xsbti.compile._

	import java.io.File

object IC extends IncrementalCompiler[Analysis, AnalyzingCompiler]
{
	def compile(in: Inputs[Analysis, AnalyzingCompiler], log: Logger): Analysis =
	{
		val setup = in.setup; import setup._
		val options = in.options; import options.{options => scalacOptions, _}
		val compilers = in.compilers; import compilers._
		val agg = new AggressiveCompile(setup.cacheFile)
		val aMap = (f: File) => m2o(analysisMap(f))
		val defClass = (f: File) => { val dc = definesClass(f); (name: String) => dc.apply(name) }
		agg(scalac, javac, sources, classpath, classesDirectory, cache, scalacOptions, javacOptions, aMap, defClass, maxErrors, order, skip)(log)
	}

	private[this] def m2o[S](opt: Maybe[S]): Option[S] = if(opt.isEmpty) None else Some(opt.get)

	def newScalaCompiler(instance: ScalaInstance, interfaceJar: File, options: ClasspathOptions, log: Logger): AnalyzingCompiler =
		new AnalyzingCompiler(instance, CompilerInterfaceProvider.constant(interfaceJar), options, log)

	def compileInterfaceJar(label: String, sourceJar: File, targetJar: File, interfaceJar: File, instance: ScalaInstance, log: Logger)
	{
		val raw = new RawCompiler(instance, sbt.ClasspathOptions.auto, log)
		AnalyzingCompiler.compileSources(sourceJar :: Nil, targetJar, interfaceJar :: Nil, label, raw, log)
	}
}
