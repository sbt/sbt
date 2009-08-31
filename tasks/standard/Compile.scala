package xsbt

	import java.io.File

trait Compile extends TrackedTaskDefinition[CompileReport]
{
	val sources: Task[Set[File]]
	val classpath: Task[Set[File]]
	val options: Task[Seq[String]]
	
	val trackedClasspath = Difference.inputs(classpath, FilesInfo.lastModified, cacheFile("classpath"))
	val trackedSource = Difference.inputs(sources, FilesInfo.hash, cacheFile("sources"))
	val trackedOptions =
	{
			import Cache._
		new Changed(options.map(_.toList), cacheFile("options"))
	}
	val invalidation = InvalidateFiles(cacheFile("dependencies/"))
	
	lazy val task = create
	def create =
		trackedClasspath { rawClasspathChanges => // detect changes to the classpath (last modified only)
			trackedSource { rawSourceChanges =>// detect changes to sources (hash only)
				val newOpts = (opts: Seq[String]) => (opts, rawSourceChanges.markAllModified, rawClasspathChanges.markAllModified) // if options changed, mark everything changed
				val sameOpts = (opts: Seq[String]) => (opts, rawSourceChanges, rawClasspathChanges)
				trackedOptions(newOpts, sameOpts) bind { // detect changes to options
					case (options, classpathChanges, sourceChanges) =>
						invalidation( classpathChanges +++ sourceChanges ) { (report, tracking) => // invalidation based on changes
							compile(sourceChanges, classpathChanges, options, report, tracking)
						}
				}
			}
		} dependsOn(sources, options)// raise these dependencies to the top for parallelism
	
	def compile(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], options: Seq[String], report: InvalidationReport[File], tracking: UpdateTracking[File]): Task[CompileReport]
	lazy val tracked = getTracked
	protected def getTracked = Seq(trackedClasspath, trackedSource, trackedOptions, invalidation)
}
class StandardCompile(val sources: Task[Set[File]], val classpath: Task[Set[File]], val options: Task[Seq[String]],
	val superclassNames: Task[Set[String]], val compilerTask: Task[AnalyzeCompiler], val cacheDirectory: File, val log: xsbti.Logger) extends Compile
{
		import Task._
		import sbinary.{DefaultProtocol, Format, Operations}
		import DefaultProtocol._
		import Operations.{fromByteArray, toByteArray}
		import scala.collection.mutable.{ArrayBuffer, Buffer, HashMap, HashSet, Map, Set => mSet}
	
	private implicit val subclassFormat: Format[DetectedSubclass] =
		asProduct4(DetectedSubclass.apply)( ds => Some(ds.source, ds.subclassName, ds.superclassName, ds.isModule))
	
	override def create = super.create dependsOn(superclassNames, compilerTask) // raise these dependencies to the top for parallelism
	def compile(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], options: Seq[String], report: InvalidationReport[File], tracking: UpdateTracking[File]): Task[CompileReport] =
	{
		val sources = report.invalid ** sourceChanges.checked // determine the sources that need recompiling (report.invalid also contains classes and libraries)
		val classpath = classpathChanges.checked
		
		(compilerTask, superclassNames) map { (compiler, superClasses) =>
			val callback = new CompileAnalysisCallback(superClasses.toArray, tracking)
			val arguments = Seq("-cp", abs(classpath).mkString(File.pathSeparator)) ++ options ++ abs(sources).toSeq
			
			compiler(arguments, callback, 100, log)
			
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
			}
		}
	}
	private def abs(f: Set[File]) = f.map(_.getAbsolutePath)
	private def readTags(allApplications: mSet[String], subclassMap: Map[String, Buffer[DetectedSubclass]], readTracking: ReadTracking[File])
	{
		for((source, tag) <- readTracking.allTags) if(tag.length > 0)
		{
			val (applications, subclasses) = fromByteArray[(List[String], List[DetectedSubclass])](tag)
			allApplications ++= applications
			subclasses.foreach(subclass => subclassMap.getOrElseUpdate(subclass.superclassName, new ArrayBuffer[DetectedSubclass]) += subclass)
		}
	}
	private final class CompileAnalysisCallback(superClasses: Array[String], tracking: UpdateTracking[File]) extends xsbti.AnalysisCallback
	{
		private var applications = List[(File,String)]()
		private var subclasses = List[DetectedSubclass]()
		def superclassNames = superClasses
		def superclassNotFound(superclassName: String) = error("Superclass not found: " + superclassName)
		def beginSource(source: File) {}
		def endSource(source: File)
		{
			if(!applications.isEmpty || !subclasses.isEmpty)
			{
				tracking.tag(source, toByteArray( (applications, subclasses) ) )
				applications = Nil
				subclasses = Nil
			}
		}
		def foundApplication(source: File, className: String) { applications ::= ( (source, className) ) }
		def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean): Unit =
			subclasses ::= DetectedSubclass(source, subclassName, superclassName, isModule)
		def sourceDependency(dependsOn: File, source: File) { tracking.dependency(source, dependsOn) }
		def jarDependency(jar: File, source: File) { tracking.use(source, jar) }
		def classDependency(clazz: File, source: File) { tracking.dependency(source, clazz) }
		def generatedClass(source: File, clazz: File) { tracking.product(source, clazz) }
	}
}

trait CompileReport extends NotNull
{
	def classes: Set[File]
	def applications: Set[String]
	def superclasses: Set[String]
	def subclasses(superclass: String): Set[DetectedSubclass]
}
final case class DetectedSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean) extends NotNull