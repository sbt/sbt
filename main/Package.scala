/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import Predef.{conforms => _, _}
	import java.io.File
	import java.util.jar.{Attributes, Manifest}
	import collection.JavaConversions._
	import Types.:+:
	import Path._

		import sbinary.{DefaultProtocol,Format}
		import DefaultProtocol.{FileFormat, immutableMapFormat, StringFormat, UnitFormat}
		import Cache.{defaultEquiv, hConsCache, hNilCache, streamFormat, wrapIn}
		import Tracked.{inputChanged, outputChanged}
		import FileInfo.{exists, existsInputCache}
		import FilesInfo.lastModified
		import lastModified.infosInputCache

sealed trait PackageOption
object Package
{
	final case class JarManifest(m: Manifest) extends PackageOption
	{
		assert(m != null)
	}
	final case class MainClass(mainClassName: String) extends PackageOption
	final case class ManifestAttributes(attributes: (Attributes.Name, String)*) extends PackageOption
	def ManifestAttributes(attributes: (String, String)*): ManifestAttributes =
	{
		val converted = for( (name,value) <- attributes ) yield (new Attributes.Name(name), value)
		new ManifestAttributes(converted : _*)
	}

	def mergeAttributes(a1: Attributes, a2: Attributes) = a1 ++= a2
	// merges `mergeManifest` into `manifest` (mutating `manifest` in the process)
	def mergeManifests(manifest: Manifest, mergeManifest: Manifest)
	{
		mergeAttributes(manifest.getMainAttributes, mergeManifest.getMainAttributes)
		val entryMap = asScalaMap(manifest.getEntries)
		for((key, value) <- mergeManifest.getEntries)
		{
			entryMap.get(key) match
			{
				case Some(attributes) => mergeAttributes(attributes, value)
				case None => entryMap put (key, value)
			}
		}
	}

	final class Configuration(val sources: Seq[(File, String)], val jar: File, val options: Seq[PackageOption])
	def apply(conf: Configuration, cacheFile: File, log: Logger)
	{
		import conf._
		val manifest = new Manifest
		val main = manifest.getMainAttributes
		for(option <- options)
		{
			option match
			{
				case JarManifest(mergeManifest) => mergeManifests(manifest, mergeManifest)
				case MainClass(mainClassName) => main.put(Attributes.Name.MAIN_CLASS, mainClassName)
				case ManifestAttributes(attributes @ _*) =>  main ++= attributes
				case _ => log.warn("Ignored unknown package option " + option)
			}
		}
		setVersion(main)

		val cachedMakeJar = inputChanged(cacheFile / "inputs") { (inChanged, inputs: Map[File, String] :+: FilesInfo[ModifiedFileInfo] :+: Manifest :+: HNil) =>
			val sources :+: _ :+: manifest :+: HNil = inputs
			outputChanged(cacheFile / "output") { (outChanged, jar: PlainFileInfo) =>
				if(inChanged || outChanged)
					makeJar(sources.toSeq, jar.file, manifest)
			}
		}

		val map = conf.sources.toMap
		val inputs = map :+: lastModified(map.keySet.toSet) :+: manifest :+: HNil
		cachedMakeJar(inputs)(() => exists(conf.jar))
	}
	def setVersion(main: Attributes)
	{
		val version = Attributes.Name.MANIFEST_VERSION
		if(main.getValue(version) eq null)
			main.put(version, "1.0")
	}
	def makeJar(sources: Seq[(File, String)], jar: File, manifest: Manifest)
	{
		println("Packaging " + jar.getAbsolutePath + " ...")
		IO.delete(jar)
		IO.jar(sources, jar, manifest)
		println("Done packaging.")
	}

	implicit def manifestEquiv: Equiv[Manifest] = defaultEquiv
	implicit def manifestFormat: Format[Manifest] = streamFormat( _ write _, in => new Manifest(in))
	
	implicit def stringMapEquiv: Equiv[Map[File, String]] = defaultEquiv
}