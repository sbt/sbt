/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

	import xsbti.api.Source
	import xsbt.api.APIFormat
	import java.io.File
	import sbinary._
	import DefaultProtocol._

object AnalysisFormats
{
	type RFF = Relation[File, File]
	type RFS = Relation[File, String]

	implicit def analysisFormat(implicit stampsF: Format[Stamps], apisF: Format[APIs], relationsF: Format[Relations]): Format[Analysis] =
		asProduct3( Analysis.Empty.copy _)( a => (a.stamps, a.apis, a.relations))(stampsF, apisF, relationsF)

	implicit def setupFormat(implicit outDirF: Format[File], optionF: Format[CompileOptions], compilerVersion: Format[String], orderF: Format[CompileOrder.Value]): Format[CompileSetup] =
		asProduct4[CompileSetup, File, CompileOptions, String, CompileOrder.Value]( (a,b,c,d) => new CompileSetup(a,b,c,d) )(s => (s.outputDirectory, s.options, s.compilerVersion, s.order))(outDirF, optionF, compilerVersion, orderF)

	implicit def stampsFormat(implicit prodF: Format[Map[File, Stamp]], srcF: Format[Map[File, Stamp]], binF: Format[Map[File, Stamp]]): Format[Stamps] =
		asProduct3( Stamps.apply _ )( s => (s.products, s.sources, s.binaries) )(prodF, srcF, binF)

	implicit def stampFormat(implicit hashF: Format[Hash], modF: Format[LastModified], existsF: Format[Exists]): Format[Stamp] =
		asUnion(hashF, modF, existsF)

	implicit def apisFormat(implicit internalF: Format[Map[File, Source]], externalF: Format[Map[String, Source]]): Format[APIs] =
		asProduct2( APIs.apply _)( as => (as.internal, as.external) )(internalF, externalF)

	implicit def relationsFormat(implicit prodF: Format[RFF], binF: Format[RFF], intF: Format[RFF], extF: Format[RFS]): Format[Relations] =
		asProduct4[Relations, RFF, RFF, RFF, RFS]( (a,b,c,d) => Relations.make(a,b,c,d) )( rs => (rs.srcProd, rs.binaryDep, rs.internalSrcDep, rs.externalDep) )(prodF, binF, intF, extF)

	implicit def relationFormat[A,B](implicit af: Format[Map[A, Set[B]]], bf: Format[Map[B, Set[A]]]): Format[Relation[A,B]] =
		asProduct2[Relation[A,B], Map[A, Set[B]], Map[B, Set[A]]]( Relation.make _ )( r => (r.forwardMap, r.reverseMap) )(af, bf)

	implicit val sourceFormat: Format[Source] =
		wrap[Source, Array[Byte]]( APIFormat.write _, APIFormat.read _)

	implicit def fileFormat: Format[File] = wrap[File, String](_.getAbsolutePath, s => new File(s))
	// can't require Format[Seq[String]] because its complexity is higher than Format[CompileOptions]
	implicit def optsFormat(implicit strF: Format[String]): Format[CompileOptions] =
		wrap[CompileOptions, (Seq[String],Seq[String])](co => (co.options, co.javacOptions), os => new CompileOptions(os._1, os._2))

	implicit val orderFormat: Format[CompileOrder.Value] = enumerationFormat(CompileOrder)
	implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = viaSeq[Seq[T], T](x => x)

	implicit def hashStampFormat: Format[Hash] = wrap[Hash, Array[Byte]](_.value, new Hash(_))
	implicit def lastModFormat: Format[LastModified] = wrap[LastModified, Long](_.value, new LastModified(_))
	implicit def existsFormat: Format[Exists] = wrap[Exists, Boolean](_.value, new Exists(_))
}
