package xsbti

import java.io.File
import scala.collection.mutable.ArrayBuffer

class TestCallback(val superclassNames: Array[String], val annotationNames: Array[String]) extends AnalysisCallback
{
	val invalidSuperclasses = new ArrayBuffer[String]
	val beganSources = new ArrayBuffer[File]
	val endedSources = new ArrayBuffer[File]
	val foundSubclasses = new ArrayBuffer[(File, String, String, Boolean)]
	val foundAnnotated = new ArrayBuffer[(File, String, String, Boolean)]
	val sourceDependencies = new ArrayBuffer[(File, File)]
	val jarDependencies = new ArrayBuffer[(File, File)]
	val classDependencies = new ArrayBuffer[(File, File)]
	val products = new ArrayBuffer[(File, File)]
	val applications = new ArrayBuffer[(File, String)]

	def superclassNotFound(superclassName: String) { invalidSuperclasses += superclassName }
	def beginSource(source: File) { beganSources += source }
	def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean): Unit =
		foundSubclasses += ((source, subclassName, superclassName, isModule))
	def foundAnnotated(source: File, className: String, annotationName: String, isModule: Boolean): Unit =
		foundAnnotated += ((source, className, annotationName, isModule))
	def sourceDependency(dependsOn: File, source: File) { sourceDependencies += ((dependsOn, source)) }
	def jarDependency(jar: File, source: File) { jarDependencies += ((jar, source)) }
	def classDependency(clazz: File, source: File) { classDependencies += ((clazz, source)) }
	def generatedClass(source: File, module: File) { products += ((source, module)) }
	def endSource(source: File) { endedSources += source }
	def foundApplication(source: File, className: String) { applications += ((source, className)) }
	def api(source: File, sourceAPI: xsbti.api.Source) = ()
}