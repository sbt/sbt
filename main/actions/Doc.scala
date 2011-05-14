/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import java.io.File
	import compiler.AnalyzingCompiler

	import Predef.{conforms => _, _}
	import Types.:+:
	import Path._

	import sbinary.DefaultProtocol.FileFormat
	import Cache.{defaultEquiv, hConsCache, hNilCache, seqCache, seqFormat, streamFormat, StringFormat, UnitFormat, wrapIn}
	import Tracked.{inputChanged, outputChanged}
	import FilesInfo.{exists, hash, lastModified}

final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler)
{
	final def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger)
	{
		log.info(actionStartMessage(label))
		if(sources.isEmpty)
			log.info(ActionNothingToDoMessage)
		else
		{
			IO.delete(outputDirectory)
			IO.createDirectory(outputDirectory)
			compiler.doc(sources, classpath, outputDirectory, options, maximumErrors, log)
			log.info(ActionSuccessfulMessage)
		}
	}
	def actionStartMessage(label: String) = "Generating API documentation for " + label + " sources..."
	val ActionNothingToDoMessage = "No sources specified."
	val ActionSuccessfulMessage = "API documentation generation successful."

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
