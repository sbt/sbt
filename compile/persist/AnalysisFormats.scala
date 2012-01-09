/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

	import xsbti.api.Source
	import xsbti.{Position,Problem,Severity}
	import xsbti.compile.CompileOrder
	import java.io.File
	import sbinary._
	import DefaultProtocol._
	import Logger.{m2o, position, problem}

object AnalysisFormats
{
	type RFF = Relation[File, File]
	type RFS = Relation[File, String]


		import System.{currentTimeMillis => now}
	val start = now
		def time(label: String) =
		{
			val end = now
			println(label + ": " + (end - start) + " ms")
		}

	def debug[T](label: String, f: Format[T]): Format[T] = new Format[T]
	{
		def reads(in: Input): T =
		{
			time(label + ".read.start")
			val r = f.reads(in)
			time(label + ".read.end")
			r
		}
		def writes(out: Output, t: T)
		{
			time(label + ".write.start")
			f.writes(out,t)
			time(label + ".write.end")
		}
	}

	implicit def analysisFormat(implicit stampsF: Format[Stamps], apisF: Format[APIs], relationsF: Format[Relations], infosF: Format[SourceInfos]): Format[Analysis] =
		asProduct4( Analysis.Empty.copy _)( a => (a.stamps, a.apis, a.relations, a.infos))(stampsF, apisF, relationsF, infosF)

	implicit def infosFormat(implicit infoF: Format[Map[File, SourceInfo]]): Format[SourceInfos] =
		wrap[SourceInfos, Map[File, SourceInfo]]( _.allInfos, SourceInfos.make _)

	implicit def infoFormat(implicit infoF: Format[Problem]): Format[SourceInfo] =
		wrap[SourceInfo, (Seq[Problem],Seq[Problem])](si => (si.reportedProblems, si.unreportedProblems), { case (a,b) => SourceInfos.makeInfo(a,b)})

	implicit def problemFormat(implicit posF: Format[Position], msgF: Format[String], sevF: Format[Severity]): Format[Problem] =
		asProduct3(problem _)( p => (p.position, p.message, p.severity))

	implicit def positionFormat: Format[Position] =
		asProduct7( position _ )( p => (m2o(p.line), p.lineContent, m2o(p.offset), m2o(p.pointer), m2o(p.pointerSpace), m2o(p.sourcePath), m2o(p.sourceFile)))

	implicit val fileOptionFormat: Format[Option[File]] = optionsAreFormat[File](fileFormat)
	implicit val integerFormat: Format[Integer] = wrap[Integer, Int](_.toInt, Integer.valueOf)
	implicit val severityFormat: Format[Severity] =
		wrap[Severity, Byte]( _.ordinal.toByte, b => Severity.values.apply(b.toInt) )


	implicit def setupFormat(implicit outDirF: Format[File], optionF: Format[CompileOptions], compilerVersion: Format[String], orderF: Format[CompileOrder]): Format[CompileSetup] =
		asProduct4[CompileSetup, File, CompileOptions, String, CompileOrder]( (a,b,c,d) => new CompileSetup(a,b,c,d) )(s => (s.outputDirectory, s.options, s.compilerVersion, s.order))(outDirF, optionF, compilerVersion, orderF)

	implicit def stampsFormat(implicit prodF: Format[Map[File, Stamp]], srcF: Format[Map[File, Stamp]], binF: Format[Map[File, Stamp]], nameF: Format[Map[File, String]]): Format[Stamps] =
		asProduct4( Stamps.apply _ )( s => (s.products, s.sources, s.binaries, s.classNames) )(prodF, srcF, binF, nameF)

	implicit def stampFormat(implicit hashF: Format[Hash], modF: Format[LastModified], existsF: Format[Exists]): Format[Stamp] =
		asUnion(hashF, modF, existsF)

	implicit def apisFormat(implicit internalF: Format[Map[File, Source]], externalF: Format[Map[String, Source]]): Format[APIs] =
		asProduct2( APIs.apply _)( as => (as.internal, as.external) )(internalF, externalF)

	implicit def relationsFormat(implicit prodF: Format[RFF], binF: Format[RFF], intF: Format[RFF], extF: Format[RFS], csF: Format[RFS]): Format[Relations] =
		asProduct5[Relations, RFF, RFF, RFF, RFS, RFS]( (a,b,c,d,e) => Relations.make(a,b,c,d,e) )( rs => (rs.srcProd, rs.binaryDep, rs.internalSrcDep, rs.externalDep, rs.classes) )(prodF, binF, intF, extF, csF)

	implicit def relationFormat[A,B](implicit af: Format[Map[A, Set[B]]], bf: Format[Map[B, Set[A]]]): Format[Relation[A,B]] =
		asProduct2[Relation[A,B], Map[A, Set[B]], Map[B, Set[A]]]( Relation.make _ )( r => (r.forwardMap, r.reverseMap) )(af, bf)

	implicit val sourceFormat: Format[Source] = xsbt.api.SourceFormat

	implicit def fileFormat: Format[File] = wrap[File, String](_.getAbsolutePath, s => new File(s))
	// can't require Format[Seq[String]] because its complexity is higher than Format[CompileOptions]
	implicit def optsFormat(implicit strF: Format[String]): Format[CompileOptions] =
		wrap[CompileOptions, (Seq[String],Seq[String])](co => (co.options, co.javacOptions), os => new CompileOptions(os._1, os._2))

	implicit val orderFormat: Format[CompileOrder] =
	{
		val values = CompileOrder.values
		wrap[CompileOrder, Int](_.ordinal, values)
	}
	implicit def seqFormat[T](implicit optionFormat: Format[T]): Format[Seq[T]] = viaSeq[Seq[T], T](x => x)

	implicit def hashStampFormat: Format[Hash] = wrap[Hash, Array[Byte]](_.value, new Hash(_))
	implicit def lastModFormat: Format[LastModified] = wrap[LastModified, Long](_.value, new LastModified(_))
	implicit def existsFormat: Format[Exists] = wrap[Exists, Boolean](_.value, new Exists(_))
}
