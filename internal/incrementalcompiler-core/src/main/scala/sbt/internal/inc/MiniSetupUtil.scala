/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package internal
package inc

import xsbti.compile.{ MiniSetup, CompileOrder, Output => APIOutput, SingleOutput, MultipleOutput, MiniOptions }
import java.io.File

object MiniSetupUtil {
  // Equiv[CompileOrder.Value] dominates Equiv[MiniSetup]
  implicit def equivCompileSetup(implicit equivOutput: Equiv[APIOutput], equivOpts: Equiv[MiniOptions], equivComp: Equiv[String] /*, equivOrder: Equiv[CompileOrder]*/ ): Equiv[MiniSetup] = new Equiv[MiniSetup] {
    def equiv(a: MiniSetup, b: MiniSetup) = {
      // For some reason, an Equiv[Nothing] or some such is getting injected into here now, and borking all our results.
      // We hardcode these to use the Equiv defined in this class.
      def sameOutput = MiniSetupUtil.equivOutput.equiv(a.output, b.output)
      def sameOptions = MiniSetupUtil.equivOpts.equiv(a.options, b.options)
      def sameCompiler = equivComp.equiv(a.compilerVersion, b.compilerVersion)
      def sameOrder = a.order == b.order
      def sameNameHasher = a.nameHashing == b.nameHashing
      sameOutput &&
        sameOptions &&
        sameCompiler &&
        sameOrder && // equivOrder.equiv(a.order, b.order)
        sameNameHasher
    }
  }
  implicit val equivFile: Equiv[File] = new Equiv[File] {
    def equiv(a: File, b: File) = a.getAbsoluteFile == b.getAbsoluteFile
  }
  implicit val equivOutput: Equiv[APIOutput] = new Equiv[APIOutput] {
    implicit val outputGroupsOrdering = Ordering.by((og: MultipleOutput.OutputGroup) => og.sourceDirectory)
    def equiv(out1: APIOutput, out2: APIOutput) = (out1, out2) match {
      case (m1: MultipleOutput, m2: MultipleOutput) =>
        (m1.outputGroups.length == m2.outputGroups.length) &&
          (m1.outputGroups.sorted zip m2.outputGroups.sorted forall {
            case (a, b) =>
              equivFile.equiv(a.sourceDirectory, b.sourceDirectory) && equivFile.equiv(a.outputDirectory, b.outputDirectory)
          })
      case (s1: SingleOutput, s2: SingleOutput) =>
        equivFile.equiv(s1.outputDirectory, s2.outputDirectory)
      case _ =>
        false
    }
  }
  implicit val equivOpts: Equiv[MiniOptions] = new Equiv[MiniOptions] {
    def equiv(a: MiniOptions, b: MiniOptions) = {
      (a.scalacOptions sameElements b.scalacOptions) &&
        (a.javacOptions sameElements b.javacOptions)
    }
  }
  implicit val equivCompilerVersion: Equiv[String] = new Equiv[String] {
    def equiv(a: String, b: String) = a == b
  }

  implicit val equivOrder: Equiv[CompileOrder] = new Equiv[CompileOrder] {
    def equiv(a: CompileOrder, b: CompileOrder) = a == b
  }
}
