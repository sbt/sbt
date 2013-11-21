/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

import java.io.File
import sbinary._
import DefaultProtocol.{FileFormat => _, _}
import xsbti.api._
import xsbti.compile.{Output => APIOutput, SingleOutput, MultipleOutput}
import MultipleOutput.OutputGroup


/**
 * sbinary serialization format for Analysis objects.
 *
 * Uses an intern pool for entities that are repeated many times in relations: File and Compilation instances.
 *
 * Has state, so cannot be an object. To use, wild-card import the implicits from an instance of this class.
 */
final class InternedAnalysisFormats
{
	// Formats that reads/write internable objects directly (i.e., not via the intern pool).
	// Note: Not implicit. We only want to use the full format explicitly, in specific places (such as when
	// serializing the pool itself).
	val fullFileFormat: Format[File] =  AnalysisFormats.fileFormat

	// Format that reads/writes files via the intern pool.
	val filePool = new InternPoolFormats[File](fullFileFormat)
	implicit val fileFormat = filePool.itemFormat

	// A convenient wrapper class for pool serialization. We can add more pools here in the future if needed.
	final class Pools(var fileP: InternPool[File])

	def initPools(analysis: Analysis) = new Pools(
		filePool.initPool(allFiles(analysis))
	)

	val poolsFormat: Format[Pools] = wrap[Pools, InternPool[File]](_.fileP, new Pools(_))(filePool.poolFormat)

	// Get all files mentioned in an Analysis.
	// We take the union of all possible file sets, even though in theory some of these should be contained in others.
	// This is safer in cases where the Analysis has discrepancies, for whatever reason.
	def allFiles(analysis: Analysis): Array[File] = {
		def all(rel: Relation[File, File]) = rel._1s ++ rel._2s
		(analysis.stamps.allInternalSources ++ analysis.stamps.allBinaries ++ analysis.stamps.allProducts ++
		 analysis.stamps.classNames.keySet ++ analysis.apis.allInternalSources ++
		 all(analysis.relations.srcProd) ++ all(analysis.relations.binaryDep) ++ all(analysis.relations.internalSrcDep) ++
		 analysis.relations.externalDep._1s ++ analysis.relations.classes._1s ++
		 analysis.infos.allInfos.keySet).toArray
	}

	// Format that tacks a serialized version of the File and Compilation intern pools in front of the serialized Analysis.
	def tupleToAnalysis(pools: Pools, stamps: Stamps, apis: APIs, relations: Relations, infos: SourceInfos, compilations: Compilations) = {
		Analysis.Empty.copy(stamps, apis, relations, infos, compilations) // Ignore the pools. Their work is done.
	}
	def analysisToTuple(a: Analysis) = (initPools(a), a.stamps, a.apis, a.relations, a.infos, a.compilations)
	implicit def analysisFormat(implicit stampsF: Format[Stamps], apisF: Format[APIs], relationsF: Format[Relations],
															infosF: Format[SourceInfos], compilationsF: Format[Compilations]): Format[Analysis] =
		asProduct6(tupleToAnalysis)(analysisToTuple)(poolsFormat, stampsF, apisF, relationsF, infosF, compilationsF)

	// Formats in which we use the fullFileFormat. We don't want to use the pool when serializing CompileSetup.
	implicit val outputGroupFormat: Format[OutputGroup] =
		asProduct2((a: File,b: File) => new OutputGroup{ def sourceDirectory = a; def outputDirectory = b })({ out => (out.sourceDirectory, out.outputDirectory) })(fullFileFormat, fullFileFormat)

	val multipleOutputFormat: Format[MultipleOutput] =
		wrap[MultipleOutput, Array[OutputGroup]](_.outputGroups, { groups => new MultipleOutput { def outputGroups = groups }	})

	val singleOutputFormat: Format[SingleOutput] =
		wrap[SingleOutput, File](_.outputDirectory,{ out => new SingleOutput{def outputDirectory = out } })(fullFileFormat)

	implicit val outputFormat: Format[APIOutput] = asUnion(singleOutputFormat, multipleOutputFormat)

	// We use these implementations unchanged.
	// TODO: Refactor out so we can import them with a wildcard?
	implicit def relationFormat[A,B](implicit af: Format[Map[A, Set[B]]], bf: Format[Map[B, Set[A]]]) = AnalysisFormats.relationFormat(af, bf)
	implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = AnalysisFormats.seqFormat(optionFormat)
	implicit val integerFormat = AnalysisFormats.integerFormat
	implicit val severityFormat = AnalysisFormats.severityFormat
	implicit val orderFormat = AnalysisFormats.orderFormat
	implicit val existsFormat = AnalysisFormats.existsFormat
	implicit val infoFormat = AnalysisFormats.infoFormat
	implicit val infosFormat = AnalysisFormats.infosFormat
	implicit val problemFormat = AnalysisFormats.problemFormat
	implicit val positionFormat = AnalysisFormats.positionFormat
	implicit val sourceFormat = AnalysisFormats.sourceFormat
	implicit val apisFormat = AnalysisFormats.apisFormat
	implicit val relationsSourceFormat = AnalysisFormats.relationsSourceFormat
	implicit val relationsSourceDependenciesFormat = AnalysisFormats.relationsSourceDependenciesFormat
	implicit val relationsFormat = AnalysisFormats.relationsFormat
	implicit val optsFormat = AnalysisFormats.optsFormat
	implicit val hashStampFormat = AnalysisFormats.hashStampFormat
	implicit val lastModFormat = AnalysisFormats.lastModFormat
	implicit val stampFormat = AnalysisFormats.stampFormat
	implicit val stampsFormat = AnalysisFormats.stampsFormat
	implicit val setupFormat = AnalysisFormats.setupFormat
	implicit val compilationsFormat = AnalysisFormats.compilationsFormat
}
