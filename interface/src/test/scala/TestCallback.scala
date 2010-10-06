package xsbti

	import java.io.File
	import scala.collection.mutable.ArrayBuffer

class TestCallback extends AnalysisCallback
{
	val beganSources = new ArrayBuffer[File]
	val endedSources = new ArrayBuffer[File]
	val sourceDependencies = new ArrayBuffer[(File, File)]
	val binaryDependencies = new ArrayBuffer[(File, String, File)]
	val products = new ArrayBuffer[(File, File)]
	val apis = new ArrayBuffer[(File, xsbti.api.Source)]

	def beginSource(source: File) { beganSources += source }

	def sourceDependency(dependsOn: File, source: File) { sourceDependencies += ((dependsOn, source)) }
	def binaryDependency(binary: File, name: String, source: File) { binaryDependencies += ((binary, name, source)) }
	def generatedClass(source: File, module: File) { products += ((source, module)) }
	def endSource(source: File) { endedSources += source }

	def api(source: File, sourceAPI: xsbti.api.Source) { apis += ((source, sourceAPI)) }
}