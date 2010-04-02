/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */
package xsbt

	import java.io.File
	import xsbt.api.{APIFormat, SameAPI}
	import xsbti.api.Source

trait CompileImpl[R]
{
	def apply(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], outputDirectory: File, options: Seq[String]): Task[R]
	def tracked: Seq[Tracked]
}
final class Compile[R](val cacheDirectory: File, val sources: Task[Set[File]], val classpath: Task[Set[File]],
	val outputDirectory: Task[File], val options: Task[Seq[String]], compileImpl: CompileImpl[R]) extends TrackedTaskDefinition[R]
{
	val trackedClasspath = Difference.inputs(classpath, FilesInfo.lastModified, cacheFile("classpath"))
	val trackedSource = Difference.inputs(sources, FilesInfo.hash, cacheFile("sources"))
	val trackedOptions =
	{
			import Cache._
			import Task._
		new Changed((outputDirectory, options) map ( "-d" :: _.getAbsolutePath :: _.toList), cacheFile("options"))
	}

	val task =
		trackedClasspath { rawClasspathChanges => // detect changes to the classpath (last modified only)
			trackedSource { rawSourceChanges => // detect changes to sources (hash only)
				val newOpts = (opts: Seq[String]) => (opts, rawSourceChanges.markAllModified, rawClasspathChanges.markAllModified) // if options changed, mark everything changed
				val sameOpts = (opts: Seq[String]) => (opts, rawSourceChanges, rawClasspathChanges)
				trackedOptions(newOpts, sameOpts) bind { // detect changes to options
					case (options, sourceChanges, classpathChanges) =>
						outputDirectory bind { outDir => 
							FileUtilities.createDirectory(outDir)
							compileImpl(sourceChanges, classpathChanges, outDir, options)
						}
				}
			}
		} dependsOn(sources, classpath, options, outputDirectory)// raise these dependencies to the top for parallelism

	lazy val tracked = Seq(trackedClasspath, trackedSource, trackedOptions) ++ compileImpl.tracked
}

object AggressiveCompile
{
	def apply(sources: Task[Set[File]], classpath: Task[Set[File]], outputDirectory: Task[File], options: Task[Seq[String]],
		cacheDirectory: File, compilerTask: Task[AnalyzingCompiler], log: CompileLogger): Compile[Set[File]] =
	{
		val implCache = new File(cacheDirectory, "deps")
		val baseCache = new File(cacheDirectory, "inputs")
		val impl = new AggressiveCompile(implCache, compilerTask, log)
		new Compile(baseCache, sources, classpath, outputDirectory, options, impl)
	}
}

class AggressiveCompile(val cacheDirectory: File, val compilerTask: Task[AnalyzingCompiler], val log: CompileLogger) extends CompileImpl[Set[File]]
{
	def apply(sourceChanges: ChangeReport[File], classpathChanges: ChangeReport[File], outputDirectory: File, options: Seq[String]): Task[Set[File]] =
		compilerTask bind { compiler =>
			tracking { tracker =>
			timestamp { tstamp =>
				Task {
					log.info("Removed sources: \n\t" + sourceChanges.removed.mkString("\n\t"))
					log.info("Added sources: \n\t" + sourceChanges.added.mkString("\n\t"))
					log.info("Modified sources: \n\t" + (sourceChanges.modified -- sourceChanges.added -- sourceChanges.removed).mkString("\n\t"))

					val classpath = classpathChanges.checked
					val readTracker = tracker.read
					// directories that are no longer on the classpath, not necessarily removed from the filesystem
					val removedDirectories = classpathChanges.removed.filter(_.isDirectory)
					log.info("Directories no longer on classpath:\n\t" + removedDirectories.mkString("\n\t"))

					def uptodate(time: Long, files: Iterable[File]) = files.forall(_.lastModified < time)
					def isOutofdate(file: File, related: => Iterable[File]) = !file.exists || !uptodate(file.lastModified, related)
					def invalidatesUses(file: File) = !file.exists || file.lastModified > tstamp
					def isProductOutofdate(product: File) = isOutofdate(product, readTracker.sources(product))
					def inRemovedDirectory(file: File) = removedDirectories.exists(dir => FileUtilities.relativize(dir, file).isDefined)
					def isUsedOutofdate(file: File) = classpathChanges.modified(file) || inRemovedDirectory(file) || invalidatesUses(file)

					// these are products that no longer exist or are older than the sources that produced them
					val outofdateProducts = readTracker.allProducts.filter(isProductOutofdate)
					log.info("Out of date products:\n\t" + outofdateProducts.mkString("\n\t"))
					// used classes and jars that a) no longer exist b) are no longer on the classpath or c) are newer than the sources that use them
					val outofdateUses = readTracker.allUsed.filter(isUsedOutofdate)
					log.info("Out of date binaries:\n\t" + outofdateUses.mkString("\n\t"))
					
					val modifiedSources = sourceChanges.modified
					val invalidatedByClasspath = outofdateUses.flatMap(readTracker.usedBy)
					log.info("Invalidated by classpath changes:\n\t" + invalidatedByClasspath.mkString("\n\t"))
					val invalidatedByRemovedSrc = sourceChanges.removed.flatMap(readTracker.dependsOn)
					log.info("Invalidated by removed sources:\n\t" + invalidatedByRemovedSrc.mkString("\n\t"))
					val productsOutofdate = outofdateProducts.flatMap(readTracker.sources)
					log.info("Invalidated by out of date products:\n\t" + productsOutofdate.mkString("\n\t"))

					val rawInvalidatedSources = modifiedSources ++ invalidatedByClasspath ++ invalidatedByRemovedSrc ++ productsOutofdate
					val invalidatedSources = scc(readTracker, rawInvalidatedSources)
					val sources = invalidatedSources.filter(_.exists)
					val previousAPIMap = Map() ++ sources.map { src => (src, APIFormat.read(readTracker.tag(src))) }
					val invalidatedProducts = outofdateProducts ++ products(readTracker, invalidatedSources)

					val transitiveIfNeeded = InvalidateTransitive(tracker, sources)
					tracker.removeAll(invalidatedProducts ++ classpathChanges.modified ++ (invalidatedSources -- sources))
					tracker.pending(sources)
					FileUtilities.delete(invalidatedProducts)

					log.info("All initially invalidated sources:\n\t" + sources.mkString("\n\t"))
					if(!sources.isEmpty)
					{
						val newAPIMap = doCompile(sources, classpath, outputDirectory, options, tracker, compiler, log)
						val apiChanged = sources filter { src => !sameAPI(previousAPIMap, newAPIMap, src) }
						log.info("Sources with API changes:\n\t" + apiChanged.mkString("\n\t"))
						lazy val nextSources = transitiveIfNeeded.invalid ** sourceChanges.checked
						def nextDone = nextSources.forall(sources contains _)
						val finalAPIMap =
							// if either nothing changed or everything was already recompiled, stop here
							if(apiChanged.isEmpty || nextDone) newAPIMap
							else
							{
								//val changedNames = TopLevel.nameChanges(newAPIMap.values, previousAPIMap.values)
								InvalidateTransitive.clean(tracker, FileUtilities.delete, transitiveIfNeeded)
								log.info("All sources invalidated by API changes:\n\t" + nextSources.mkString("\n\t"))
								doCompile(nextSources, classpath, outputDirectory, options, tracker, compiler, log)
							}
						finalAPIMap.foreach { case (src, api) => tracker.tag(src, APIFormat.write(api)) }
					}
					Set() ++ tracker.read.allProducts
				}
			}}
		}
	def products(tracker: ReadTracking[File], srcs: Set[File]): Set[File] = srcs.flatMap(tracker.products)

	def doCompile(sources: Set[File], classpath: Set[File], outputDirectory: File, options: Seq[String], tracker: UpdateTracking[File], compiler: AnalyzingCompiler, log: CompileLogger): scala.collection.Map[File, Source] =
	{
		val callback = new APIAnalysisCallback(tracker)
		log.debug("Compiling using compiler " + compiler)
		compiler(sources, classpath, outputDirectory, options, callback, 100, log)
		callback.apiMap
	}

		import sbinary.DefaultProtocol.FileFormat
	val tracking = new DependencyTracked(cacheDirectory, true, (files: File) => FileUtilities.delete(files))
	val timestamp = new Timestamp(new File(cacheDirectory,"timestamp"))
	def tracked = Seq(tracking, timestamp)

	def sameAPI[T](a: scala.collection.Map[T, Source], b: scala.collection.Map[T, Source], t: T): Boolean = sameAPI(a.get(t), b.get(t))
	def sameAPI(a: Option[Source], b: Option[Source]): Boolean =
		if(a.isEmpty) b.isEmpty else (b.isDefined && SameAPI(a.get, b.get))

	// TODO: implement
	def scc(readTracker: ReadTracking[File], sources: Set[File]) = sources
}


private final class APIAnalysisCallback(tracking: UpdateTracking[File]) extends xsbti.AnalysisCallback
{
	val apiMap = new scala.collection.mutable.HashMap[File, Source]

	def sourceDependency(dependsOn: File, source: File) { tracking.dependency(source, dependsOn) }
	def jarDependency(jar: File, source: File) { tracking.use(source, jar) }
	def classDependency(clazz: File, source: File) { tracking.dependency(source, clazz) }
	def generatedClass(source: File, clazz: File) { tracking.product(source, clazz) }
	def api(source: File, api: xsbti.api.Source) { apiMap(source) = api }

	def superclassNames = Array()
	def annotationNames = Array()
	def superclassNotFound(superclassName: String) {}
	def beginSource(source: File)  {}
	def endSource(source: File) {}
	def foundApplication(source: File, className: String) {}
	def foundSubclass(source: File, subclassName: String, superclassName: String, isModule: Boolean) {}
	def foundAnnotated(source: File, className: String, annotationName: String, isModule: Boolean) {}
}
