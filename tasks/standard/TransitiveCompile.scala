/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package xsbt

	import java.io.File


trait TransitiveCompile extends CompileImpl[CompileReport] with WithCache
{
	final val invalidation = InvalidateFiles(cacheFile("dependencies/"))
	def tracked = Seq(invalidation)

	def apply(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], outputDirectory: File, options: Seq[String]): Task[CompileReport] =
		// doesn't notice if classes are removed
		invalidation( classpathChanges +++ sourceChanges ) { (report, tracking) => // invalidation based on changes
			compile(sourceChanges, classpathChanges, outputDirectory, options, report, tracking)
		}
	def compile(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], outputDirectory: File,
			options: Seq[String], report: InvalidationReport[File], tracking: UpdateTracking[File]): Task[CompileReport]
}

class StandardCompile(val sources: Task[Set[File]], val classpath: Task[Set[File]], val outputDirectory: Task[File], val options: Task[Seq[String]],
	val superclassNames: Task[Set[String]], val compilerTask: Task[AnalyzingCompiler], val cacheDirectory: File, val log: CompileLogger) extends TransitiveCompile
{
		import Task._
		import scala.collection.mutable.{ArrayBuffer, Buffer, HashMap, HashSet, Map, Set => mSet}

	def compile(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], outputDirectory: File,
		options: Seq[String], report: InvalidationReport[File], tracking: UpdateTracking[File]): Task[CompileReport] =
	{
		val sources = report.invalid ** sourceChanges.checked // determine the sources that need recompiling (report.invalid also contains classes and libraries)
		val classpath = classpathChanges.checked
		compile(sources, classpath, outputDirectory, options, tracking)
	}
	def compile(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], tracking: UpdateTracking[File]): Task[CompileReport] =
	{
		(compilerTask, superclassNames) map { (compiler, superClasses) =>
			if(!sources.isEmpty)
			{
				val callback = new CompileAnalysisCallback(superClasses.toArray, tracking)
				log.debug("Compile task calling compiler " + compiler)
				compiler(sources, classpath, outputDirectory, options, callback, 100, log)
			}
			val readTracking = tracking.read
			val applicationSet = new HashSet[String]
			val subclassMap = new HashMap[String, Buffer[DetectedSubclass]]
			readTags(applicationSet, subclassMap, readTracking)
			new CompileReport
			{
				val superclasses = superClasses
				def subclasses(superclass: String) = Set() ++ subclassMap.getOrElse(superclass, Nil)
				val applications = Set() ++ applicationSet
				val classes = Set() ++ readTracking.allProducts
				override def toString =
				{
					val superStrings = superclasses.map(superC => superC + " >: \n\t\t" + subclasses(superC).mkString("\n\t\t"))
					val applicationsPart = if(applications.isEmpty) Nil else Seq("Applications") ++ applications
					val lines = Seq("Compilation Report:", sources.size + " sources", classes.size + " classes") ++ superStrings
					lines.mkString("\n\t")
				}
			}
		}
	}
	private def abs(f: Set[File]) = f.map(_.getAbsolutePath)
	private def readTags(allApplications: mSet[String], subclassMap: Map[String, Buffer[DetectedSubclass]], readTracking: ReadTracking[File])
	{
		for((source, tag) <- readTracking.allTags) if(tag.length > 0)
		{
			val (applications, subclasses) = Tag.fromBytes(tag)
			allApplications ++= applications
			subclasses.foreach(subclass => subclassMap.getOrElseUpdate(subclass.superclassName, new ArrayBuffer[DetectedSubclass]) += subclass)
		}
	}
	private final class CompileAnalysisCallback(superClasses: Array[String], tracking: UpdateTracking[File]) extends xsbti.AnalysisCallback
	{
		private var applications = List[String]()
		private var subclasses = List[DetectedSubclass]()
		def superclassNames = superClasses
		def annotationNames = error("TODO")
		def superclassNotFound(superclassName: String) = error("Superclass not found: " + superclassName)
		def beginSource(source: File) {}
		def endSource(source: File)
		{
			if(!applications.isEmpty || !subclasses.isEmpty)
			{
				tracking.tag(source, Tag.toBytes(applications, subclasses) )
				applications = Nil
				subclasses = Nil
			}
		}
		def foundApplication(source: File, className: String) { applications ::= className }
		def foundAnnotated(source: File, className: String, annotationName: String, isModule: Boolean) { error("TODO") }
		def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean): Unit =
			subclasses ::= DetectedSubclass(source, subclassName, superclassName, isModule)
		def sourceDependency(dependsOn: File, source: File) { tracking.dependency(source, dependsOn) }
		def jarDependency(jar: File, source: File) { tracking.use(source, jar) }
		def classDependency(clazz: File, source: File) { tracking.dependency(source, clazz) }
		def generatedClass(source: File, clazz: File) { tracking.product(source, clazz) }
		def api(source: File, api: xsbti.api.Source) = ()
	}
}

object Tag
{
		import sbinary.{DefaultProtocol, Format, Operations}
		import DefaultProtocol._
	private implicit val subclassFormat: Format[DetectedSubclass] =
		asProduct4(DetectedSubclass.apply)( ds => (ds.source, ds.subclassName, ds.superclassName, ds.isModule))
	def toBytes(applications: List[String], subclasses: List[DetectedSubclass]) = CacheIO.toBytes((applications, subclasses))
	def fromBytes(bytes: Array[Byte]) = CacheIO.fromBytes( ( List[String](), List[DetectedSubclass]() ) )(bytes)
}
trait CompileReport extends NotNull
{
	def classes: Set[File]
	def applications: Set[String]
	def superclasses: Set[String]
	def subclasses(superclass: String): Set[DetectedSubclass]
}
final case class DetectedSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean) extends NotNull