/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah, Indrajit Raychaudhuri
 */
package sbt

	import java.io.File
	import compiler.{AnalyzingCompiler, JavaCompiler}

	import Predef.{conforms => _, _}
	import Types.:+:
	import Path._

	import sbinary.DefaultProtocol.FileFormat
	import Cache.{defaultEquiv, hConsCache, hNilCache, IntFormat, seqCache, seqFormat, streamFormat, StringFormat, UnitFormat, wrapIn}
	import Tracked.{inputChanged, outputChanged}
	import FilesInfo.{exists, hash, lastModified}

object RawCompileLike
{
	type Gen = (Seq[File], Seq[File], File, Seq[String], Int, Logger) => Unit

	private def optionFiles(options: Seq[String], fileInputOpts: Seq[String]): List[File] =
	{
		@annotation.tailrec
		def loop(opt: List[String], result: List[File]): List[File] = {
			opt.dropWhile(! fileInputOpts.contains(_)) match {
				case List(_, fileOpt, tail @ _*) =>
				{
					val file = new File(fileOpt)
					if(file.isFile) loop(tail.toList, file :: result)
					else loop(tail.toList, result)
				}
				case Nil | List(_) => result
			}
		}
		loop(options.toList, Nil)
	}

	def cached(cache: File, doCompile: Gen): Gen = cached(cache, Seq(), doCompile)
	def cached(cache: File, fileInputOpts: Seq[String], doCompile: Gen): Gen = (sources, classpath, outputDirectory, options, maxErrors, log) =>
	{
		type Inputs = FilesInfo[HashFileInfo] :+: FilesInfo[ModifiedFileInfo] :+: String :+: File :+: Seq[String] :+: Int :+: HNil
		val inputs: Inputs = hash(sources.toSet ++ optionFiles(options, fileInputOpts)) :+: lastModified(classpath.toSet) :+: classpath.absString :+: outputDirectory :+: options :+: maxErrors :+: HNil
		implicit val stringEquiv: Equiv[String] = defaultEquiv
		implicit val fileEquiv: Equiv[File] = defaultEquiv
		implicit val intEquiv: Equiv[Int] = defaultEquiv
		val cachedComp = inputChanged(cache / "inputs") { (inChanged, in: Inputs) =>
			outputChanged(cache / "output") { (outChanged, outputs: FilesInfo[PlainFileInfo]) =>
				if(inChanged || outChanged)
					doCompile(sources, classpath, outputDirectory, options, maxErrors, log)
				else
					log.debug("Uptodate: " + outputDirectory.getAbsolutePath)
			}
		}
		cachedComp(inputs)(() => exists(outputDirectory.***.get.toSet))
	}
	def prepare(description: String, doCompile: Gen): Gen = (sources, classpath, outputDirectory, options, maxErrors, log) =>
	{
		if(sources.isEmpty)
			log.info("No sources available, skipping " + description + "...")
		else
		{
			log.info(description.capitalize + " to " + outputDirectory.absolutePath + "...")
			IO.delete(outputDirectory)
			IO.createDirectory(outputDirectory)
			doCompile(sources, classpath, outputDirectory, options, maxErrors, log)
			log.info(description.capitalize + " successful.")
		}
	}
	def filterSources(f: File => Boolean, doCompile: Gen): Gen = (sources, classpath, outputDirectory, options, maxErrors, log) =>
		doCompile(sources filter f, classpath, outputDirectory, options, maxErrors, log)

	def rawCompile(instance: ScalaInstance, cpOptions: ClasspathOptions): Gen = (sources, classpath, outputDirectory, options, maxErrors, log) =>
	{
		val compiler = new sbt.compiler.RawCompiler(instance, cpOptions, log)
		compiler(sources, classpath, outputDirectory, options)
	}
	def compile(label: String, cache: File, instance: ScalaInstance, cpOptions: ClasspathOptions): Gen =
		cached(cache, prepare(label + " sources", rawCompile(instance, cpOptions)))

	val nop: Gen = (sources, classpath, outputDirectory, options, maxErrors, log) => ()
}
