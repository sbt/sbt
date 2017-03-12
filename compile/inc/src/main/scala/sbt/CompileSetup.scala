/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import xsbti.compile.{ CompileOrder, Output => APIOutput, SingleOutput, MultipleOutput }
import java.io.File

// this class exists because of Scala's restriction on implicit parameter search.
//  We cannot require an implicit parameter Equiv[Seq[String]] to construct Equiv[CompileSetup]
//    because complexity(Equiv[Seq[String]]) > complexity(Equiv[CompileSetup])
//     (6 > 4)
final class CompileOptions(val options: Seq[String], val javacOptions: Seq[String]) {
  override def toString = s"CompileOptions(scalac=$options, javac=$javacOptions)"
}
final class CompileSetup(val output: APIOutput,
                         val options: CompileOptions,
                         val compilerVersion: String,
                         val order: CompileOrder,
                         val nameHashing: Boolean) {
  @deprecated(
    "Use the other overloaded variant of the constructor that takes `nameHashing` value, instead.",
    "0.13.2"
  )
  def this(output: APIOutput, options: CompileOptions, compilerVersion: String, order: CompileOrder) = {
    this(output, options, compilerVersion, order, false)
  }
  override def toString = s"""CompileSetup(
                             |  options = $options
                             |  compilerVersion = $compilerVersion
                             |  order = $order
                             |  nameHashing = $nameHashing
                             |  output = $output
                             |)""".stripMargin
}

object CompileSetup {
  // Equiv[CompileOrder.Value] dominates Equiv[CompileSetup]
  implicit def equivCompileSetup(
      implicit equivOutput: Equiv[APIOutput],
      equivOpts: Equiv[CompileOptions],
      equivComp: Equiv[String] /*, equivOrder: Equiv[CompileOrder]*/
  ): Equiv[CompileSetup] = new Equiv[CompileSetup] {
    def equiv(a: CompileSetup, b: CompileSetup) = {
      // For some reason, an Equiv[Nothing] or some such is getting injected into here now, and borking all our results.
      // We hardcode these to use the Equiv defined in this class.
      def sameOutput = CompileSetup.equivOutput.equiv(a.output, b.output)
      def sameOptions = CompileSetup.equivOpts.equiv(a.options, b.options)
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
              equivFile.equiv(a.sourceDirectory, b.sourceDirectory) && equivFile
                .equiv(a.outputDirectory, b.outputDirectory)
          })
      case (s1: SingleOutput, s2: SingleOutput) =>
        equivFile.equiv(s1.outputDirectory, s2.outputDirectory)
      case _ =>
        false
    }
  }
  implicit val equivOpts: Equiv[CompileOptions] = new Equiv[CompileOptions] {
    def equiv(a: CompileOptions, b: CompileOptions) =
      (a.options sameElements b.options) &&
        (a.javacOptions sameElements b.javacOptions)
  }
  implicit val equivCompilerVersion: Equiv[String] = new Equiv[String] {
    def equiv(a: String, b: String) = a == b
  }

  implicit val equivOrder: Equiv[CompileOrder] = new Equiv[CompileOrder] {
    def equiv(a: CompileOrder, b: CompileOrder) = a == b
  }
}
