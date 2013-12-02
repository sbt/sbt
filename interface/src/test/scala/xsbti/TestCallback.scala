package xsbti

import java.io.File
import scala.collection.mutable.ArrayBuffer
import xsbti.api.SourceAPI

class TestCallback(override val memberRefAndInheritanceDeps: Boolean = false) extends AnalysisCallback
{
	val sourceDependencies = new ArrayBuffer[(File, File, Boolean)]
	val binaryDependencies = new ArrayBuffer[(File, String, File, Boolean)]
	val products = new ArrayBuffer[(File, File, String)]
	val apis: scala.collection.mutable.Map[File, SourceAPI] = scala.collection.mutable.Map.empty

	def sourceDependency(dependsOn: File, source: File, inherited: Boolean) { sourceDependencies += ((dependsOn, source, inherited)) }
	def binaryDependency(binary: File, name: String, source: File, inherited: Boolean) { binaryDependencies += ((binary, name, source, inherited)) }
	def generatedClass(source: File, module: File, name: String) { products += ((source, module, name)) }

	def api(source: File, sourceAPI: SourceAPI): Unit = {
		assert(!apis.contains(source), s"The `api` method should be called once per source file: $source")
		apis(source) = sourceAPI
	}
	def problem(category: String, pos: xsbti.Position, message: String, severity: xsbti.Severity, reported: Boolean) {}
}
