package xsbti

	import java.io.File
	import scala.collection.mutable.ArrayBuffer

class TestCallback extends AnalysisCallback
{
	val beganSources = new ArrayBuffer[File]
	val endedSources = new ArrayBuffer[File]
	val sourceDependencies = new ArrayBuffer[(File, File, Boolean)]
	val binaryDependencies = new ArrayBuffer[(File, String, File, Boolean)]
	val products = new ArrayBuffer[(File, File, String)]
	val apis = new ArrayBuffer[(File, xsbti.api.SourceAPI)]

	def beginSource(source: File) { beganSources += source }

	def sourceDependency(dependsOn: File, source: File, inherited: Boolean) { sourceDependencies += ((dependsOn, source, inherited)) }
	def binaryDependency(binary: File, name: String, source: File, inherited: Boolean) { binaryDependencies += ((binary, name, source, inherited)) }
	def generatedClass(source: File, module: File, name: String) { products += ((source, module, name)) }
	def endSource(source: File) { endedSources += source }

	def api(source: File, sourceAPI: xsbti.api.SourceAPI) { apis += ((source, sourceAPI)) }
	def problem(category: String, pos: xsbti.Position, message: String, severity: xsbti.Severity, reported: Boolean) {}
}