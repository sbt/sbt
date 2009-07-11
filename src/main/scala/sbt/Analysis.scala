/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

trait TaskAnalysis[Source, Product, External] extends NotNull
{
	import scala.collection.Set
	def save(): Option[String]
	def revert(): Option[String]
	def clear(): Unit
	
	def allSources: Set[Source]
	def allProducts: Set[Product]
	def allExternals: Set[External]
	
	def sourceDependencies(source: Source): Option[Set[Source]]
	def products(source: Source): Option[Set[Product]]
	def externalDependencies(external: External): Option[Set[Source]]
	
	def addSource(source: Source): Unit
	def addExternalDependency(dependsOn: External, source: Source): Unit
	def addSourceDependency(dependsOn: Source, source: Source): Unit
	def addProduct(source: Source, product: Product): Unit
	
	def removeSource(source: Source): Unit
	def removeDependent(source: Source): Unit
	def removeDependencies(source: Source): Option[Set[Source]]
	def removeExternalDependency(external: External): Unit
}

import java.io.File
import BasicAnalysis._
import impl.MapUtilities.{add, all, read, mark, readOnlyIterable, write}
import scala.collection.mutable.{HashMap, HashSet, ListBuffer, Map, Set}

sealed class BasicAnalysis(analysisPath: Path, projectPath: Path, log: Logger) extends TaskAnalysis[Path, Path, File]
{
	private val sourceDependencyMap: Map[Path, Set[Path]] = new HashMap
	private val productMap: Map[Path, Set[Path]] = new HashMap
	private val externalDependencyMap: Map[File, Set[Path]] = new HashMap
	
	final type AnyMapToSource = Map[K, Set[Path]] forSome {type K}
	final type AnySourceMap = Map[Path, T] forSome {type T}
	final type AnySourceSetMap = Map[Path, Set[T]] forSome {type T}
	final type AnyMap = Map[K, V] forSome { type K; type V }
	
	protected def mapsToClear = List[AnyMap](sourceDependencyMap, productMap, externalDependencyMap)
	protected def mapsToRemoveSource = List[AnySourceMap](sourceDependencyMap, productMap)
	protected def mapsToRemoveDependent = List[AnyMapToSource](sourceDependencyMap, externalDependencyMap)
	protected def mapsToMark = List[AnySourceSetMap](sourceDependencyMap, productMap)
	
	def clear()
	{
		for(map <- mapsToClear)
			map.clear()
	}
	def removeSource(source: Path)
	{
		for(sourceProducts <- productMap.get(source))
			FileUtilities.clean(sourceProducts, true, log)
		for(map <- mapsToRemoveSource)
			map -= source
	}
	def removeSelfDependency(source: Path)
	{
		for(deps <- sourceDependencyMap.get(source))
			deps -= source
	}
	def removeDependent(source: Path)
	{
		for(map <- mapsToRemoveDependent; deps <- map.values)
			deps -= source
	}
	def removeDependencies(source: Path) = sourceDependencyMap.removeKey(source)
	def removeExternalDependency(dep: File) = externalDependencyMap.removeKey(dep.getAbsoluteFile)
	
	def externalDependencies(external: File) = externalDependencyMap.get(external.getAbsoluteFile)
	def sourceDependencies(source: Path) = sourceDependencyMap.get(source)
	def products(sources: Iterable[Path]): Iterable[Path] =
	{
		val buffer = new ListBuffer[Path]
		for(source <- sources; sourceProducts <- productMap.get(source))
			buffer ++= sourceProducts
		buffer.readOnly
	}
	def products(source: Path) = productMap.get(source)
	
	def allSources = sourceDependencyMap.keySet
	def allProducts: Set[Path] = HashSet(flatten(productMap.values.toList) : _*)
	def allExternals = externalDependencyMap.keySet
	
	def allExternalDependencies = readOnlyIterable(externalDependencyMap)
	def allDependencies = readOnlyIterable(sourceDependencyMap)
	
	def addSourceDependency(on: Path, from: Path) = add(on, from, sourceDependencyMap)
	def addExternalDependency(on: File, from: Path) = add(on.getAbsoluteFile, from, externalDependencyMap)
	def addProductDependency(on: Path, from: Path) =
	{
		for( (source, _) <- productMap.find(_._2.contains(on)) )
			addSourceDependency(source, from)
	}
	def addProduct(source: Path, file: Path) = add(source, file, productMap)
	def addSource(source: Path) =
	{
		for(map <- mapsToMark)
			mark(source, map)
	}
	
	import Format._ // get implicits for data types
	implicit val path: Format[Path] = Format.path(projectPath)
	implicit val pathSet: Format[Set[Path]] = Format.set
	
	protected def backedMaps: Iterable[Backed[_,_]] = 
		Backed(sourceDependencyMap, DependenciesLabel, DependenciesFileName) ::
		Backed(productMap, GeneratedLabel, GeneratedFileName) ::
		Backed(externalDependencyMap, ExternalDependenciesLabel, ExternalDependenciesFileName) ::
		Nil
	
	def revert() = load()
	private def loadBacked[Key,Value](b: Backed[Key,Value]) = read(b.map, analysisPath / b.name, log)(b.keyFormat, b.valueFormat)
	private def storeBacked[Key,Value](b: Backed[Key,Value]) = write(b.map, b.label, analysisPath / b.name, log)(b.keyFormat, b.valueFormat)
	final def load(): Option[String] = Control.lazyFold(backedMaps.toList)(backed =>loadBacked(backed))
	final def save(): Option[String] = Control.lazyFold(backedMaps.toList)(backed => storeBacked(backed))
}
object BasicAnalysis
{
	private def flatten(s: Iterable[Set[Path]]): Seq[Path] = s.flatMap(x => x.toSeq).toSeq

	val GeneratedFileName = "generated_files"
	val DependenciesFileName = "dependencies"
	val ExternalDependenciesFileName = "external"
	
	val GeneratedLabel = "Generated Classes"
	val DependenciesLabel = "Source Dependencies"
	val ExternalDependenciesLabel = "External Dependencies"
	
	def load(analysisPath: Path, projectPath: Path, log: Logger): Either[String, BasicAnalysis] =
	{
		val analysis = new BasicAnalysis(analysisPath, projectPath, log)
		analysis.load().toLeft(analysis)
	}
}
object CompileAnalysis
{
	val HashesFileName = "hashes"
	val TestsFileName = "tests"
	val ApplicationsFileName = "applications"
	val ProjectDefinitionsName = "projects"
	
	val HashesLabel = "Source Hashes"
	val TestsLabel = "Tests"
	val ApplicationsLabel = "Classes with main methods"
	val ProjectDefinitionsLabel = "Project Definitions"
	
	def load(analysisPath: Path, projectPath: Path, log: Logger): Either[String, CompileAnalysis] =
	{
		val analysis = new CompileAnalysis(analysisPath, projectPath, log)
		analysis.load().toLeft(analysis)
	}
}
import CompileAnalysis._
import Format._ // get implicits for data types
sealed class BasicCompileAnalysis protected (analysisPath: Path, projectPath: Path, log: Logger) extends BasicAnalysis(analysisPath, projectPath, log)
{
	/*private */val hashesMap = new HashMap[Path, Array[Byte]]
	
	override protected def mapsToClear = hashesMap :: super.mapsToClear
	override protected def mapsToRemoveSource = hashesMap :: super.mapsToRemoveSource
	
	def setHash(source: Path, hash: Array[Byte]) { hashesMap(source) = hash }
	def clearHash(source: Path) { hashesMap.removeKey(source) }
	def hash(source: Path) = hashesMap.get(source)
	def clearHashes() { hashesMap.clear() }
	
	def getClasses(sources: PathFinder, outputDirectory: Path): PathFinder =
		Path.lazyPathFinder
		{
			val basePath = (outputDirectory ##)
			for(c <- products(sources.get)) yield
				Path.relativize(basePath, c).getOrElse(c)
		}
		
	implicit val stringSet: Format[Set[String]] = Format.set
	override protected def backedMaps = Backed(hashesMap, HashesLabel, HashesFileName) :: super.backedMaps.toList
}
private[sbt] final class BuilderCompileAnalysis(analysisPath: Path, projectPath: Path, log: Logger) extends BasicCompileAnalysis(analysisPath, projectPath, log)
{
	private val projectDefinitionMap = new HashMap[Path, Set[String]]
	override protected def mapsToClear = projectDefinitionMap :: super.mapsToClear
	override protected def mapsToRemoveSource = projectDefinitionMap :: super.mapsToRemoveSource
	def allProjects = all(projectDefinitionMap)
	def addProjectDefinition(source: Path, className: String) = add(source, className, projectDefinitionMap)
	
	override protected def backedMaps =
		Backed(projectDefinitionMap, ProjectDefinitionsLabel, ProjectDefinitionsName) ::
		super.backedMaps
}
final class CompileAnalysis(analysisPath: Path, projectPath: Path, log: Logger) extends BasicCompileAnalysis(analysisPath, projectPath, log)
{
	private val testMap = new HashMap[Path, Set[TestDefinition]]
	private val applicationsMap = new HashMap[Path, Set[String]]
	def allTests = all(testMap)
	def allApplications = all(applicationsMap)
	def addTest(source: Path, test: TestDefinition) = add(source, test, testMap)
	def addApplication(source: Path, className: String) = add(source, className, applicationsMap)
	
	def testSourceMap: Map[String, Path] =
	{
		val map = new HashMap[String, Path]
		for( (source, tests) <- testMap; test <- tests) map(test.testClassName) = source
		map
	}
	
	override protected def mapsToClear = applicationsMap :: testMap :: super.mapsToClear
	override protected def mapsToRemoveSource = applicationsMap :: testMap :: super.mapsToRemoveSource
	
	implicit val testSet: Format[Set[TestDefinition]] = Format.set
	override protected def backedMaps =
		Backed(testMap, TestsLabel, TestsFileName) ::
		Backed(applicationsMap, ApplicationsLabel, ApplicationsFileName) ::
		super.backedMaps
}
/** A map that is persisted in a properties file named 'name' and with 'label'.  'keyFormat' and 'valueFormat' are used to (de)serialize. */
final case class Backed[Key, Value](map: Map[Key, Value], label: String, name: String)(implicit val keyFormat: Format[Key], val valueFormat: Format[Value]) extends NotNull