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
	import Cache.{defaultEquiv, hConsCache, hNilCache, seqCache, seqFormat, streamFormat, StringFormat, UnitFormat, wrapIn}
	import Tracked.{inputChanged, outputChanged}
	import FilesInfo.{exists, hash, lastModified}

object Doc {
	def apply(maximumErrors: Int, compiler: AnalyzingCompiler) = new Scaladoc(maximumErrors, compiler)
	def apply(maximumErrors: Int, compiler: sbt.compiler.Javadoc) = new Javadoc(maximumErrors, compiler)
}
sealed trait Doc {
	type Gen = (Seq[File], Seq[File], File, Seq[String], Int, Logger) => Unit

	def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger): Unit

	final def generate(variant: String, label: String, docf: Gen, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maxErrors: Int, log: Logger) {
		val logSnip = variant + " API documentation"
		if(sources.isEmpty)
			log.info("No sources available, skipping " + logSnip + "...")
		else
		{
			log.info("Generating " + logSnip + " for " + label + " sources to " + outputDirectory.absolutePath + "...")
			IO.delete(outputDirectory)
			IO.createDirectory(outputDirectory)
			docf(sources, classpath, outputDirectory, options, maxErrors, log)
			log.info(logSnip + " generation successful.")
		}
	}

	def cached(cache: File, label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		type Inputs = FilesInfo[HashFileInfo] :+: FilesInfo[ModifiedFileInfo] :+: String :+: File :+: Seq[String] :+: HNil
		val inputs: Inputs = hash(sources.toSet) :+: lastModified(classpath.toSet) :+: classpath.absString :+: outputDirectory :+: options :+: HNil
		implicit val stringEquiv: Equiv[String] = defaultEquiv
		implicit val fileEquiv: Equiv[File] = defaultEquiv
		val cachedDoc = inputChanged(cache / "inputs") { (inChanged, in: Inputs) =>
			outputChanged(cache / "output") { (outChanged, outputs: FilesInfo[PlainFileInfo]) =>
				if(inChanged || outChanged)
					apply(label, sources, classpath, outputDirectory, options, log)
				else
					log.debug("Doc uptodate: " + outputDirectory.getAbsolutePath)
			}
		}
		cachedDoc(inputs)(() => exists(outputDirectory.***.get.toSet))
	}
}
final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler) extends Doc
{
	def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		generate("Scala", label, compiler.doc, sources, classpath, outputDirectory, options, maximumErrors, log)
	}
}
final class Javadoc(maximumErrors: Int, doc: sbt.compiler.Javadoc) extends Doc
{
	def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		// javadoc doesn't handle *.scala properly, so we evict them from javadoc sources list.
		generate("Java", label, doc.doc, sources.filterNot(_.name.endsWith(".scala")), classpath, outputDirectory, options, maximumErrors, log)
	}
}
