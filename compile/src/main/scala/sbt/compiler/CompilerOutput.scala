/* sbt -- Simple Build Tool
 * Copyright 2012  Eugene Vigdorchik
 */

package sbt
package compiler

	import xsbti.compile.{Output, SingleOutput, MultipleOutput}
	import java.io.File

object CompileOutput {
	def apply(dir: File): Output = new SingleOutput {
		def outputDirectory = dir
	}

	def apply(groups: (File, File)*): Output = new MultipleOutput {
		def outputGroups = groups.toArray map {
			case (src, out) => new MultipleOutput.OutputGroup {
				def sourceDirectory = src
				def outputDirectory = out
			}
		}
	}
}
