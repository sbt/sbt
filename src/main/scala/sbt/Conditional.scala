/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

trait Conditional[Source, Product, External] extends NotNull
{
	type AnalysisType <: TaskAnalysis[Source, Product, External]
	val analysis: AnalysisType = loadAnalysis
	
	protected def loadAnalysis: AnalysisType
	protected def log: Logger

	protected def productType: String
	protected def productTypePlural: String
	
	protected def sourcesToProcess: Iterable[Source]
	
	protected def sourceExists(source: Source): Boolean
	protected def sourceLastModified(source: Source): Long
	
	protected def productExists(product: Product): Boolean
	protected def productLastModified(product: Product): Long
	
	protected def externalInfo(externals: Iterable[External]): Iterable[(External, ExternalInfo)]
	
	protected def execute(cAnalysis: ConditionalAnalysis): Option[String]
	
	final case class ExternalInfo(available: Boolean, lastModified: Long) extends NotNull
	trait ConditionalAnalysis extends NotNull
	{
		def dirtySources: Iterable[Source]
		def cleanSources: Iterable[Source]
		def directlyModifiedSourcesCount: Int
		def invalidatedSourcesCount: Int
		def removedSourcesCount: Int
	}
	
	final def run =
	{
		val result = execute(analyze)
		processingComplete(result.isEmpty)
		result
	}
	private def analyze =
	{
		import scala.collection.mutable.HashSet
		
		val sourcesSnapshot = sourcesToProcess
		val removedSources = new HashSet[Source]
		removedSources ++= analysis.allSources
		removedSources --= sourcesSnapshot
		val removedCount = removedSources.size
		for(removed <- removedSources)
			analysis.removeDependent(removed)
		
		val unmodified = new HashSet[Source]
		val modified = new HashSet[Source]
		
		for(source <- sourcesSnapshot)
		{
			if(isSourceModified(source))
			{
				log.debug("Source " + source + " directly modified.")
				modified += source
			}
			else
			{
				log.debug("Source " + source + " unmodified.")
				unmodified += source
			}
		}
		val directlyModifiedCount = modified.size
		for((external, info) <- externalInfo(analysis.allExternals))
		{
			val dependentSources = analysis.externalDependencies(external).getOrElse(Set.empty)
			if(info.available)
			{
				val dependencyLastModified = info.lastModified
				for(dependentSource <- dependentSources; dependentProducts <- analysis.products(dependentSource))
				{
					dependentProducts.find(p => productLastModified(p) < dependencyLastModified) match
					{
						case Some(modifiedProduct) =>
						{
							log.debug(productType + " " + modifiedProduct + " older than external dependency " + external)
							unmodified -= dependentSource
							modified += dependentSource
						}
						case None => ()
					}
				}
			}
			else
			{
				log.debug("External dependency " + external + " not found.")
				unmodified --= dependentSources
				modified ++= dependentSources
				analysis.removeExternalDependency(external)
			}
		}
		
		val handled = new scala.collection.mutable.HashSet[Source]
		val transitive = !java.lang.Boolean.getBoolean("sbt.intransitive")
		def markModified(changed: Iterable[Source]) { for(c <- changed if !handled.contains(c)) markSourceModified(c) }
		def markSourceModified(src: Source)
		{
			unmodified -= src
			modified += src
			handled += src
			if(transitive)
				markDependenciesModified(src)
		}
		def markDependenciesModified(src: Source) { analysis.removeDependencies(src).map(markModified) }

		markModified(modified.toList)
		if(transitive)
			removedSources.foreach(markDependenciesModified)
		
		for(changed <- removedSources ++ modified)
			analysis.removeSource(changed)
		
		new ConditionalAnalysis
		{
			def dirtySources = wrap.Wrappers.readOnly(modified)
			def cleanSources = wrap.Wrappers.readOnly(unmodified)
			def directlyModifiedSourcesCount = directlyModifiedCount
			def invalidatedSourcesCount = dirtySources.size - directlyModifiedCount
			def removedSourcesCount = removedCount
			override def toString =
			{
				"  Source analysis: " + directlyModifiedSourcesCount + " new/modified, " +
					invalidatedSourcesCount + " indirectly invalidated, " +
					removedSourcesCount + " removed."
			}
		}
	}
	
	protected def checkLastModified = true
	protected def noProductsImpliesModified = true
	protected def isSourceModified(source: Source) =
	{
		analysis.products(source) match
		{
			case None =>
			{
				log.debug("New file " + source)
				true
			}
			case Some(sourceProducts) =>
			{
				val sourceModificationTime = sourceLastModified(source)
				def isOutofdate(p: Product) =
					!productExists(p) || (checkLastModified && productLastModified(p) < sourceModificationTime)
				
				sourceProducts.find(isOutofdate) match
				{
					case Some(modifiedProduct) =>
						log.debug("Outdated " + productType + ": " + modifiedProduct + " for source " + source)
						true
					case None =>
						if(noProductsImpliesModified && sourceProducts.isEmpty)
						{
							// necessary for change detection that depends on last modified
							log.debug("Source " + source + " has no products, marking it modified.")
							true
						}
						else
							false
				}
			}
		}
	}
	protected def processingComplete(success: Boolean)
	{
		if(success)
		{
			analysis.save()
			log.info("  Post-analysis: " + analysis.allProducts.toSeq.length + " " + productTypePlural + ".")
		}
		else
			analysis.revert()
	}
}

abstract class AbstractCompileConfiguration extends NotNull
{
	def label: String
	def sources: PathFinder
	def outputDirectory: Path
	def classpath: PathFinder
	def analysisPath: Path
	def projectPath: Path
	def log: Logger
	def options: Seq[String]
	def javaOptions: Seq[String]
	def maxErrors: Int
	def compileOrder: CompileOrder.Value
}
abstract class CompileConfiguration extends AbstractCompileConfiguration
{
	def testDefinitionClassNames: Iterable[String]
}
import java.io.File
class CompileConditional(override val config: CompileConfiguration) extends AbstractCompileConditional(config)
{
	import config._
	type AnalysisType = CompileAnalysis
	protected def constructAnalysis(analysisPath: Path, projectPath: Path, log: Logger) =
		new CompileAnalysis(analysisPath, projectPath, log)
	protected def analysisCallback = new CompileAnalysisCallback
	protected class CompileAnalysisCallback extends BasicCompileAnalysisCallback(projectPath, testDefinitionClassNames, analysis)
	{
		def foundSubclass(sourcePath: Path, subclassName: String, superclassName: String, isModule: Boolean)
		{
			analysis.addTest(sourcePath, TestDefinition(isModule, subclassName, superclassName))
		}
	}
}
abstract class AbstractCompileConditional(val config: AbstractCompileConfiguration) extends Conditional[Path, Path, File]
{
	import config._
	type AnalysisType <: BasicCompileAnalysis
	protected def loadAnalysis =
	{
		val a = constructAnalysis(analysisPath, projectPath, log)
		for(errorMessage <- a.load())
			error(errorMessage)
		a
	}
	protected def constructAnalysis(analysisPath: Path, projectPath: Path, log: Logger): AnalysisType
	
	protected def log = config.log
	
	protected def productType = "class"
	protected def productTypePlural = "classes"
	protected def sourcesToProcess = sources.get
	
	protected def sourceExists(source: Path) = source.asFile.exists
	protected def sourceLastModified(source: Path) = source.asFile.lastModified
	
	protected def productExists(product: Path) = product.asFile.exists
	protected def productLastModified(product: Path) = product.asFile.lastModified
	
	protected def externalInfo(externals: Iterable[File]) =
	{
		val (classpathJars, classpathDirs) = ClasspathUtilities.buildSearchPaths(classpath.get)
		for(external <- externals) yield
		{
			val available = external.exists && ClasspathUtilities.onClasspath(classpathJars, classpathDirs, external)
			if(!available)
				log.debug("External " + external + (if(external.exists) " not on classpath." else " does not exist."))
			(external, ExternalInfo(available, external.lastModified))
		}
	}
	
	import ChangeDetection.{LastModifiedOnly, HashOnly, HashAndLastModified, HashAndProductsExist}
	protected def changeDetectionMethod: ChangeDetection.Value = HashAndProductsExist
	override protected def checkLastModified = changeDetectionMethod != HashAndProductsExist
	override protected def noProductsImpliesModified = changeDetectionMethod == LastModifiedOnly
	override protected def isSourceModified(source: Path) =
		changeDetectionMethod match
		{
			case HashAndLastModified | HashAndProductsExist =>
				// behavior will differ because of checkLastModified
				// hash modified must come first so that the latest hash is calculated for every source
				hashModified(source) || super.isSourceModified(source)
			case HashOnly => hashModified(source)
			case LastModifiedOnly => super.isSourceModified(source)
		}
	
	import scala.collection.mutable.{Buffer, ListBuffer}
	private val newHashes: Buffer[(Path, Option[Array[Byte]])] = new ListBuffer
	private def warnHashError(source: Path, message: String)
	{
		log.warn("Error computing hash for source " + source + ": " + message)
		newHashes += ((source, None))
	}
	protected def hashModified(source: Path) =
	{
		source.isDirectory ||
		(analysis.hash(source) match
		{
			case None =>
				log.debug("Source " + source + " had no hash, marking modified.")
				Hash(source, log).fold(err => warnHashError(source, err), newHash => newHashes += ((source, Some(newHash))))
				true
			case Some(oldHash) =>
			{
				Hash(source, log) match
				{
					case Left(err) =>
						warnHashError(source, err)
						log.debug("Assuming source is modified because of error.")
						true
					case Right(newHash) =>
						newHashes += ((source, Some(newHash)))
						val different = !(oldHash deepEquals newHash)
						if(different)
							log.debug("Hash for source " + source + " changed (was " + Hash.toHex(oldHash) +
							", is now " + Hash.toHex(newHash) + "), marking modified.")
						different
				}
			}
		})
	}
	protected def execute(executeAnalysis: ConditionalAnalysis) =
	{
		log.info(executeAnalysis.toString)
		finishHashes()
		import executeAnalysis.dirtySources
		
		// the output directory won't show up in the classpath unless it exists, so do this before classpath.get
		val outputDir = outputDirectory.asFile
		FileUtilities.createDirectory(outputDir, log)
		
		val cp = classpath.get
		if(!dirtySources.isEmpty)
			checkClasspath(cp)
		val classpathString = Path.makeString(cp)
		val id = AnalysisCallback.register(analysisCallback)
		val allOptions = (("-Xplugin:" + FileUtilities.sbtJar.getAbsolutePath) ::
			("-P:sbt-analyzer:callback:" + id.toString) :: Nil) ++ options
		val r = (new Compile(config.maxErrors))(label, dirtySources, classpathString, outputDirectory, allOptions, javaOptions, compileOrder, log)
		AnalysisCallback.unregister(id)
		if(log.atLevel(Level.Debug))
		{
			/** This checks that the plugin accounted for all classes in the output directory.*/
			val classes = scala.collection.mutable.HashSet(analysis.allProducts.toSeq: _*)
			var missed = 0
			for(c <- (outputDirectory ** GlobFilter("*.class")).get)
			{
				if(!classes.contains(c))
				{
					missed += 1
					log.debug("Missed class: " + c)
				}
			}
			log.debug("Total missed classes: " + missed)
		}
		r
	}
	private def finishHashes()
	{
		if(changeDetectionMethod == LastModifiedOnly)
			analysis.clearHashes()
		else
		{
			for((path, hash) <- newHashes)
			{
				hash match
				{
					case None => analysis.clearHash(path)
					case Some(hash) => analysis.setHash(path, hash)
				}
			}
		}
		newHashes.clear()
	}
	private def checkClasspath(cp: Iterable[Path])
	{
		import scala.collection.mutable.{HashMap, HashSet, Set}
		val collisions = new HashMap[String, Set[Path]]
		for(jar <- cp if ClasspathUtilities.isArchive(jar))
			collisions.getOrElseUpdate(jar.asFile.getName, new HashSet[Path]) += jar
		for((name, jars) <- collisions)
		{
			if(jars.size > 1)
			{
				log.warn("Possible duplicate classpath locations for jar " + name + ": ")
				for(jar <- jars) log.warn("\t" + jar.absolutePath)
			}
		}
	}
	
	protected def analysisCallback: AnalysisCallback
}
object ChangeDetection extends Enumeration
{
	val LastModifiedOnly, HashOnly, HashAndLastModified, HashAndProductsExist = Value
}