/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt
package inc

import java.io.File

object CompileOrder extends Enumeration
{
	val Mixed, JavaThenScala, ScalaThenJava = Value
}

// this class exists because of Scala's restriction on implicit parameter search.
//  We cannot require an implicit parameter Equiv[Seq[String]] to construct Equiv[CompileSetup]
//    because complexity(Equiv[Seq[String]]) > complexity(Equiv[CompileSetup])
//     (6 > 4)
final class CompileOptions(val options: Seq[String], val javacOptions: Seq[String])
final class CompileSetup(val outputDirectory: File, val options: CompileOptions, val compilerVersion: String, val order: CompileOrder.Value)

object CompileSetup
{
	// Equiv[CompileOrder.Value] dominates Equiv[CompileSetup]
	implicit def equivCompileSetup(implicit equivFile: Equiv[File], equivOpts: Equiv[CompileOptions], equivComp: Equiv[String]/*, equivOrder: Equiv[CompileOrder.Value]*/): Equiv[CompileSetup] = new Equiv[CompileSetup] {
		def equiv(a: CompileSetup, b: CompileSetup) =
			equivFile.equiv(a.outputDirectory, b.outputDirectory) &&
			equivOpts.equiv(a.options, b.options) &&
			equivComp.equiv(a.compilerVersion, b.compilerVersion) &&
			a.order == b.order // equivOrder.equiv(a.order, b.order)
	}
	implicit val equivOutputDirectory: Equiv[File] = new Equiv[File] {
		def equiv(a: File, b: File) = a.getAbsoluteFile == b.getAbsoluteFile
	}
	implicit val equivOpts: Equiv[CompileOptions] = new Equiv[CompileOptions] {
		def equiv(a: CompileOptions, b: CompileOptions) =
			(a.options sameElements b.options) &&
			(a.javacOptions sameElements b.javacOptions) 
	}
	implicit val equivCompilerVersion: Equiv[String] = new Equiv[String] {
		def equiv(a: String, b: String) = a == b
	}
	
	implicit val equivOrder: Equiv[CompileOrder.Value] = new Equiv[CompileOrder.Value] {
		def equiv(a: CompileOrder.Value, b: CompileOrder.Value) = a == b
	}
}