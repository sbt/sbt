/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

import scala.Predef.{ conforms => _, _ }
import java.io.File
import java.util.jar.{ Attributes, Manifest }
import scala.collection.JavaConverters._
import sbt.internal.util.Types.:+:
import sbt.io.IO

import sjsonnew.JsonFormat

import sbt.util.Logger

import sbt.internal.util.{ CacheStoreFactory, FilesInfo, HNil, ModifiedFileInfo, PlainFileInfo }
import sbt.internal.util.FileInfo.{ exists, lastModified }
import sbt.internal.util.CacheImplicits._
import sbt.internal.util.Tracked.inputChanged

sealed trait PackageOption
object Package {
  final case class JarManifest(m: Manifest) extends PackageOption {
    assert(m != null)
  }
  final case class MainClass(mainClassName: String) extends PackageOption
  final case class ManifestAttributes(attributes: (Attributes.Name, String)*) extends PackageOption
  def ManifestAttributes(attributes: (String, String)*): ManifestAttributes =
    {
      val converted = for ((name, value) <- attributes) yield (new Attributes.Name(name), value)
      new ManifestAttributes(converted: _*)
    }

  def mergeAttributes(a1: Attributes, a2: Attributes) = a1.asScala ++= a2.asScala
  // merges `mergeManifest` into `manifest` (mutating `manifest` in the process)
  def mergeManifests(manifest: Manifest, mergeManifest: Manifest): Unit = {
    mergeAttributes(manifest.getMainAttributes, mergeManifest.getMainAttributes)
    val entryMap = manifest.getEntries.asScala
    for ((key, value) <- mergeManifest.getEntries.asScala) {
      entryMap.get(key) match {
        case Some(attributes) => mergeAttributes(attributes, value)
        case None             => entryMap put (key, value)
      }
    }
  }

  final class Configuration(val sources: Seq[(File, String)], val jar: File, val options: Seq[PackageOption])
  def apply(conf: Configuration, cacheStoreFactory: CacheStoreFactory, log: Logger): Unit = {
    val manifest = new Manifest
    val main = manifest.getMainAttributes
    for (option <- conf.options) {
      option match {
        case JarManifest(mergeManifest)          => mergeManifests(manifest, mergeManifest)
        case MainClass(mainClassName)            => main.put(Attributes.Name.MAIN_CLASS, mainClassName)
        case ManifestAttributes(attributes @ _*) => main.asScala ++= attributes
        case _                                   => log.warn("Ignored unknown package option " + option)
      }
    }
    setVersion(main)

    val cachedMakeJar = inputChanged(cacheStoreFactory derive "inputs") { (inChanged, inputs: Map[File, String] :+: FilesInfo[ModifiedFileInfo] :+: Manifest :+: HNil) =>
      import exists.format
      val sources :+: _ :+: manifest :+: HNil = inputs
      inputChanged(cacheStoreFactory derive "output") { (outChanged, jar: PlainFileInfo) =>
        if (inChanged || outChanged)
          makeJar(sources.toSeq, jar.file, manifest, log)
        else
          log.debug("Jar uptodate: " + jar.file)
      }
    }

    val map = conf.sources.toMap
    val inputs = map :+: lastModified(map.keySet) :+: manifest :+: HNil
    cachedMakeJar(inputs)(exists(conf.jar))
  }
  def setVersion(main: Attributes): Unit = {
    val version = Attributes.Name.MANIFEST_VERSION
    if (main.getValue(version) eq null)
      main.put(version, "1.0")
  }
  def addSpecManifestAttributes(name: String, version: String, orgName: String): PackageOption =
    {
      import Attributes.Name._
      val attribKeys = Seq(SPECIFICATION_TITLE, SPECIFICATION_VERSION, SPECIFICATION_VENDOR)
      val attribVals = Seq(name, version, orgName)
      ManifestAttributes(attribKeys zip attribVals: _*)
    }
  def addImplManifestAttributes(name: String, version: String, homepage: Option[java.net.URL], org: String, orgName: String): PackageOption =
    {
      import Attributes.Name._
      val attribKeys = Seq(IMPLEMENTATION_TITLE, IMPLEMENTATION_VERSION, IMPLEMENTATION_VENDOR, IMPLEMENTATION_VENDOR_ID)
      val attribVals = Seq(name, version, orgName, org)
      ManifestAttributes((attribKeys zip attribVals) ++ { homepage map (h => (IMPLEMENTATION_URL, h.toString)) }: _*)
    }
  def makeJar(sources: Seq[(File, String)], jar: File, manifest: Manifest, log: Logger): Unit = {
    log.info("Packaging " + jar.getAbsolutePath + " ...")
    IO.delete(jar)
    log.debug(sourcesDebugString(sources))
    IO.jar(sources, jar, manifest)
    log.info("Done packaging.")
  }
  def sourcesDebugString(sources: Seq[(File, String)]): String =
    "Input file mappings:\n\t" + (sources map { case (f, s) => s + "\n\t  " + f } mkString ("\n\t"))

  implicit def manifestEquiv: Equiv[Manifest] = defaultEquiv
  implicit def manifestFormat: JsonFormat[Manifest] = project[Manifest, Array[Byte]](
    m => {
      val bos = new java.io.ByteArrayOutputStream()
      m write bos
      bos.toByteArray
    },
    bs => new Manifest(new java.io.ByteArrayInputStream(bs))
  )

  implicit def stringMapEquiv: Equiv[Map[File, String]] = defaultEquiv
}
