/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt
package inc

import xsbti.api.{ Source, Compilation }
import xsbti.{ Position, Problem, Severity }
import xsbti.compile.{ CompileOrder, Output => APIOutput, SingleOutput, MultipleOutput }
import xsbti.DependencyContext._
import MultipleOutput.OutputGroup
import java.io.File
import sbinary._
import DefaultProtocol._
import DefaultProtocol.tuple2Format
import Logger.{ m2o, position, problem }
import Relations.{ Source => RSource, SourceDependencies }

@deprecated("Replaced by TextAnalysisFormat. OK to remove in 0.14.", since = "0.13.1")
object AnalysisFormats {
  type RFF = Relation[File, File]
  type RFS = Relation[File, String]

  import System.{ currentTimeMillis => now }
  val start = now
  def time(label: String) =
    {
      val end = now
      println(label + ": " + (end - start) + " ms")
    }

  def debug[T](label: String, f: Format[T]): Format[T] = new Format[T] {
    def reads(in: Input): T =
      {
        time(label + ".read.start")
        val r = f.reads(in)
        time(label + ".read.end")
        r
      }
    def writes(out: Output, t: T): Unit = {
      time(label + ".write.start")
      f.writes(out, t)
      time(label + ".write.end")
    }
  }

  implicit def analysisFormat(implicit stampsF: Format[Stamps], apisF: Format[APIs], relationsF: Format[Relations],
    infosF: Format[SourceInfos], compilationsF: Format[Compilations]): Format[Analysis] =
    asProduct5(Analysis.Empty.copy _)(a => (a.stamps, a.apis, a.relations, a.infos, a.compilations))(stampsF, apisF, relationsF, infosF, compilationsF)

  implicit def infosFormat(implicit infoF: Format[Map[File, SourceInfo]]): Format[SourceInfos] =
    wrap[SourceInfos, Map[File, SourceInfo]](_.allInfos, SourceInfos.make _)

  implicit def infoFormat: Format[SourceInfo] =
    wrap[SourceInfo, (Seq[Problem], Seq[Problem])](si => (si.reportedProblems, si.unreportedProblems), { case (a, b) => SourceInfos.makeInfo(a, b) })

  implicit def problemFormat: Format[Problem] = asProduct4(problem _)(p => (p.category, p.position, p.message, p.severity))

  implicit def compilationsFormat: Format[Compilations] = {
    implicit val compilationSeqF = seqFormat(xsbt.api.CompilationFormat)
    wrap[Compilations, Seq[Compilation]](_.allCompilations, Compilations.make _)
  }

  implicit def positionFormat: Format[Position] =
    asProduct7(position _)(p => (m2o(p.line), p.lineContent, m2o(p.offset), m2o(p.pointer), m2o(p.pointerSpace), m2o(p.sourcePath), m2o(p.sourceFile)))

  implicit val fileOptionFormat: Format[Option[File]] = optionsAreFormat[File](fileFormat)
  implicit val integerFormat: Format[Integer] = wrap[Integer, Int](_.toInt, Integer.valueOf)
  implicit val severityFormat: Format[Severity] =
    wrap[Severity, Byte](_.ordinal.toByte, b => Severity.values.apply(b.toInt))

  implicit def setupFormat(implicit outputF: Format[APIOutput], optionF: Format[CompileOptions], compilerVersion: Format[String], orderF: Format[CompileOrder], nameHashingF: Format[Boolean]): Format[CompileSetup] =
    asProduct5[CompileSetup, APIOutput, CompileOptions, String, CompileOrder, Boolean]((a, b, c, d, e) => new CompileSetup(a, b, c, d, e))(s => (s.output, s.options, s.compilerVersion, s.order, s.nameHashing))(outputF, optionF, compilerVersion, orderF, nameHashingF)

  implicit val outputGroupFormat: Format[OutputGroup] =
    asProduct2((a: File, b: File) => new OutputGroup { def sourceDirectory = a; def outputDirectory = b }) { out => (out.sourceDirectory, out.outputDirectory) }(fileFormat, fileFormat)
  implicit val multipleOutputFormat: Format[MultipleOutput] =
    wrap[MultipleOutput, Array[OutputGroup]](
      (_.outputGroups),
      {
        groups =>
          new MultipleOutput {
            def outputGroups = groups
            override def toString = s"MultipleOutput($outputGroups)"
          }
      }
    )
  implicit val singleOutputFormat: Format[SingleOutput] =
    wrap[SingleOutput, File](
      (_.outputDirectory),
      { out => new SingleOutput { def outputDirectory = out } }
    )(fileFormat)
  implicit val outputFormat: Format[APIOutput] = asUnion(singleOutputFormat, multipleOutputFormat)

  implicit def stampsFormat(implicit prodF: Format[Map[File, Stamp]], srcF: Format[Map[File, Stamp]], binF: Format[Map[File, Stamp]], nameF: Format[Map[File, String]]): Format[Stamps] =
    asProduct4(Stamps.apply _)(s => (s.products, s.sources, s.binaries, s.classNames))(prodF, srcF, binF, nameF)

  implicit def stampFormat(implicit hashF: Format[Hash], modF: Format[LastModified], existsF: Format[Exists]): Format[Stamp] =
    asUnion(hashF, modF, existsF)

  implicit def apisFormat(implicit internalF: Format[Map[File, Source]], externalF: Format[Map[String, Source]]): Format[APIs] =
    asProduct2(APIs.apply _)(as => (as.internal, as.external))(internalF, externalF)

  implicit def relationsFormat(implicit prodF: Format[RFF], binF: Format[RFF], directF: Format[RSource], inheritedF: Format[RSource], memberRefF: Format[SourceDependencies], inheritanceF: Format[SourceDependencies], csF: Format[RFS], namesF: Format[RFS]): Format[Relations] =
    {
      def makeRelation(srcProd: RFF, binaryDep: RFF, direct: RSource, publicInherited: RSource,
        memberRef: SourceDependencies, inheritance: SourceDependencies, classes: RFS,
        nameHashing: Boolean, names: RFS): Relations = if (nameHashing) {
        def isEmpty(sourceDependencies: RSource): Boolean =
          sourceDependencies.internal.all.isEmpty && sourceDependencies.external.all.isEmpty
        // we check direct dependencies only because publicInherited dependencies are subset of direct
        assert(isEmpty(direct), "Direct dependencies are not empty but `nameHashing` flag is enabled.")
        val internalDependencies = InternalDependencies(Map(DependencyByMemberRef -> memberRef.internal, DependencyByInheritance -> inheritance.internal))
        val externalDependencies = ExternalDependencies(Map(DependencyByMemberRef -> memberRef.external, DependencyByInheritance -> inheritance.external))
        Relations.make(srcProd, binaryDep, internalDependencies, externalDependencies, classes, names)
      } else {
        def isEmpty(sourceDependencies: SourceDependencies): Boolean =
          sourceDependencies.internal.all.isEmpty && sourceDependencies.external.all.isEmpty
        // we check memberRef dependencies only because inheritance dependencies are subset of memberRef
        assert(isEmpty(memberRef), "Direct dependencies are not empty but `nameHashing` flag is enabled.")
        Relations.make(srcProd, binaryDep, direct, publicInherited, classes)
      }
      asProduct9[Relations, RFF, RFF, RSource, RSource, SourceDependencies, SourceDependencies, RFS, Boolean, RFS]((a, b, c, d, e, f, g, h, i) => makeRelation(a, b, c, d, e, f, g, h, i))(
        rs => (rs.srcProd, rs.binaryDep, rs.direct, rs.publicInherited, rs.memberRef, rs.inheritance, rs.classes, rs.nameHashing, rs.names))(
          prodF, binF, directF, inheritedF, memberRefF, inheritanceF, csF, implicitly[Format[Boolean]], namesF)
    }

  implicit def relationsSourceFormat(implicit internalFormat: Format[Relation[File, File]], externalFormat: Format[Relation[File, String]]): Format[RSource] =
    asProduct2[RSource, RFF, RFS]((a, b) => Relations.makeSource(a, b))(rs => (rs.internal, rs.external))

  implicit def relationsSourceDependenciesFormat(implicit internalFormat: Format[Relation[File, File]], externalFormat: Format[Relation[File, String]]): Format[SourceDependencies] =
    asProduct2[SourceDependencies, RFF, RFS]((a, b) => Relations.makeSourceDependencies(a, b))(rs => (rs.internal, rs.external))

  implicit def relationFormat[A, B](implicit af: Format[Map[A, Set[B]]], bf: Format[Map[B, Set[A]]]): Format[Relation[A, B]] =
    asProduct2[Relation[A, B], Map[A, Set[B]], Map[B, Set[A]]](Relation.make _)(r => (r.forwardMap, r.reverseMap))(af, bf)

  implicit val sourceFormat: Format[Source] = xsbt.api.SourceFormat

  implicit def fileFormat: Format[File] = wrap[File, String](_.getAbsolutePath, s => new File(s))
  // can't require Format[Seq[String]] because its complexity is higher than Format[CompileOptions]
  implicit def optsFormat(implicit strF: Format[String]): Format[CompileOptions] =
    wrap[CompileOptions, (Seq[String], Seq[String])](co => (co.options, co.javacOptions), os => new CompileOptions(os._1, os._2))

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
