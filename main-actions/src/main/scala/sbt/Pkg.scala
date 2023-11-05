/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.time.OffsetDateTime
import java.util.jar.{ Attributes, Manifest }
import scala.collection.JavaConverters._
import sbt.io.IO

import sjsonnew.{
  :*:,
  Builder,
  IsoLList,
  JsonFormat,
  LList,
  LNil,
  Unbuilder,
  deserializationError,
  flatUnionFormat4
}

import sbt.util.Logger
import sbt.util.{ CacheStoreFactory, FilesInfo, ModifiedFileInfo, PlainFileInfo }
import sbt.util.FileInfo.{ exists, lastModified }
import sbt.util.CacheImplicits._
import sbt.util.Tracked.{ inputChanged, outputChanged }
import scala.sys.process.Process
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFile, VirtualFileRef }

/**
 * == Package ==
 *
 * This module provides an API to package jar files.
 *
 * @see [[https://docs.oracle.com/javase/tutorial/deployment/jar/index.html]]
 */
object Pkg:
  def JarManifest(m: Manifest) = PackageOption.JarManifest(m)
  def MainClass(mainClassName: String) = PackageOption.MainClass(mainClassName)
  def MainfestAttributes(attributes: (Attributes.Name, String)*) =
    PackageOption.ManifestAttributes(attributes: _*)
  def ManifestAttributes(attributes: (String, String)*) = {
    val converted = for ((name, value) <- attributes) yield (new Attributes.Name(name), value)
    PackageOption.ManifestAttributes(converted: _*)
  }
  // 2010-01-01
  private val default2010Timestamp: Long = 1262304000000L
  def FixedTimestamp(value: Option[Long]) = PackageOption.FixedTimestamp(value)
  val keepTimestamps: Option[Long] = None
  val fixed2010Timestamp: Option[Long] = Some(default2010Timestamp)
  def gitCommitDateTimestamp: Option[Long] =
    try {
      Some(
        OffsetDateTime
          .parse(Process("git show -s --format=%cI").!!.trim)
          .toInstant()
          .toEpochMilli()
      )
    } catch {
      case e: Exception if e.getMessage.startsWith("Nonzero") =>
        sys.error(
          s"git repository was expected for package timestamp; use Package.fixed2010Timestamp or Package.keepTimestamps instead"
        )
    }
  def setFixedTimestamp(value: Option[Long]): PackageOption =
    FixedTimestamp(value)

  /** by default we overwrite all timestamps in JAR to epoch time 2010-01-01 for repeatable build */
  lazy val defaultTimestamp: Option[Long] =
    sys.env
      .get("SOURCE_DATE_EPOCH")
      .map(_.toLong * 1000)
      .orElse(Some(default2010Timestamp))

  def timeFromConfiguration(config: Configuration): Option[Long] =
    (config.options.collect { case t: PackageOption.FixedTimestamp => t }).headOption match
      case Some(PackageOption.FixedTimestamp(value)) => value
      case _                                         => defaultTimestamp

  def mergeAttributes(a1: Attributes, a2: Attributes) = a1.asScala ++= a2.asScala
  // merges `mergeManifest` into `manifest` (mutating `manifest` in the process)
  def mergeManifests(manifest: Manifest, mergeManifest: Manifest): Unit = {
    mergeAttributes(manifest.getMainAttributes, mergeManifest.getMainAttributes)
    val entryMap = manifest.getEntries.asScala
    for ((key, value) <- mergeManifest.getEntries.asScala) {
      entryMap.get(key) match {
        case Some(attributes) => mergeAttributes(attributes, value); ()
        case None             => entryMap.put(key, value); ()
      }
    }
  }

  /**
   * The jar package configuration. Contains all relevant information to create a jar file.
   *
   * @param sources the jar contents
   * @param jar the destination jar file
   * @param options additional package information, e.g. jar manifest, main class or manifest attributes
   */
  final class Configuration(
      val sources: Seq[(HashedVirtualFileRef, String)],
      val jar: VirtualFileRef,
      val options: Seq[PackageOption]
  )

  object Configuration:
    given IsoLList.Aux[
      Configuration,
      Vector[(HashedVirtualFileRef, String)] :*: VirtualFileRef :*: Seq[PackageOption] :*: LNil
    ] =
      import sbt.util.CacheImplicits.given
      import sbt.util.PathHashWriters.given
      LList.iso(
        (c: Configuration) =>
          ("sources", c.sources.toVector) :*: ("jar", c.jar) :*: ("options", c.options) :*: LNil,
        (in: Vector[(HashedVirtualFileRef, String)] :*: VirtualFileRef :*: Seq[PackageOption] :*:
          LNil) => Configuration(in.head, in.tail.head, in.tail.tail.head),
      )
    given JsonFormat[Configuration] = summon[JsonFormat[Configuration]]
  end Configuration

  /**
   * @param conf the package configuration that should be build
   * @param cacheStoreFactory used for jar caching. We try to avoid rebuilds as much as possible
   * @param log feedback for the user
   */
  def apply(conf: Configuration, converter: FileConverter, log: Logger): VirtualFile =
    apply(conf, converter, log, timeFromConfiguration(conf))

  /**
   * @param conf the package configuration that should be build
   * @param cacheStoreFactory used for jar caching. We try to avoid rebuilds as much as possible
   * @param log feedback for the user
   * @param time static timestamp to use for all entries, if any.
   */
  def apply(
      conf: Configuration,
      converter: FileConverter,
      log: Logger,
      time: Option[Long]
  ): VirtualFile =
    val manifest = toManifest(conf, log)
    val out = converter.toPath(conf.jar).toFile()
    val sources = conf.sources.map { case (vf, path) =>
      converter.toPath(vf).toFile() -> path
    }
    makeJar(sources, out, manifest, log, time)
    converter.toVirtualFile(out.toPath())

  def toManifest(conf: Configuration, log: Logger): Manifest =
    val manifest = new Manifest
    val main = manifest.getMainAttributes
    for option <- conf.options do
      option match
        case PackageOption.JarManifest(mergeManifest) => mergeManifests(manifest, mergeManifest); ()
        case PackageOption.MainClass(mainClassName) =>
          main.put(Attributes.Name.MAIN_CLASS, mainClassName); ()
        case PackageOption.ManifestAttributes(attributes @ _*) => main.asScala ++= attributes; ()
        case PackageOption.FixedTimestamp(value)               => ()
        case _ => log.warn("Ignored unknown package option " + option)
    setVersion(main)
    manifest

  /**
   * updates the manifest version is there is none present.
   *
   * @param main the current jar attributes
   */
  def setVersion(main: Attributes): Unit = {
    val version = Attributes.Name.MANIFEST_VERSION
    if (main.getValue(version) eq null) {
      main.put(version, "1.0")
      ()
    }
  }
  def addSpecManifestAttributes(name: String, version: String, orgName: String): PackageOption = {
    import Attributes.Name._
    val attribKeys = Seq(SPECIFICATION_TITLE, SPECIFICATION_VERSION, SPECIFICATION_VENDOR)
    val attribVals = Seq(name, version, orgName)
    PackageOption.ManifestAttributes(attribKeys.zip(attribVals): _*)
  }
  def addImplManifestAttributes(
      name: String,
      version: String,
      homepage: Option[java.net.URL],
      org: String,
      orgName: String
  ): PackageOption = {
    import Attributes.Name._

    // The ones in Attributes.Name are deprecated saying:
    //   "Extension mechanism will be removed in a future release. Use class path instead."
    val IMPLEMENTATION_VENDOR_ID = new Attributes.Name("Implementation-Vendor-Id")
    val IMPLEMENTATION_URL = new Attributes.Name("Implementation-URL")

    val attribKeys = Seq(
      IMPLEMENTATION_TITLE,
      IMPLEMENTATION_VERSION,
      IMPLEMENTATION_VENDOR,
      IMPLEMENTATION_VENDOR_ID,
    )
    val attribVals = Seq(name, version, orgName, org)
    PackageOption.ManifestAttributes(attribKeys.zip(attribVals) ++ {
      homepage map (h => (IMPLEMENTATION_URL, h.toString))
    }: _*)
  }

  def makeJar(
      sources: Seq[(File, String)],
      jar: File,
      manifest: Manifest,
      log: Logger,
      time: Option[Long]
  ): Unit = {
    val path = jar.getAbsolutePath
    log.debug("Packaging " + path + " ...")
    if (jar.exists)
      if (jar.isFile)
        IO.delete(jar)
      else
        sys.error(path + " exists, but is not a regular file")
    log.debug(sourcesDebugString(sources))
    IO.jar(sources, jar, manifest, time)
    log.debug("Done packaging.")
  }
  def sourcesDebugString(sources: Seq[(File, String)]): String =
    "Input file mappings:\n\t" + (sources map { case (f, s) => s + "\n\t  " + f } mkString ("\n\t"))

  given manifestFormat: JsonFormat[Manifest] = projectFormat[Manifest, Array[Byte]](
    m => {
      val bos = new java.io.ByteArrayOutputStream()
      m write bos
      bos.toByteArray
    },
    bs => new Manifest(new java.io.ByteArrayInputStream(bs))
  )
end Pkg

enum PackageOption:
  case JarManifest(m: Manifest)
  case MainClass(mainClassName: String)
  case ManifestAttributes(attributes: (Attributes.Name, String)*)
  case FixedTimestamp(value: Option[Long])

object PackageOption:
  import Pkg.manifestFormat

  private given jarManifestFormat: JsonFormat[PackageOption.JarManifest] =
    new JsonFormat[PackageOption.JarManifest]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): PackageOption.JarManifest =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val m = unbuilder.readField[Manifest]("m")
            unbuilder.endObject()
            PackageOption.JarManifest(m)
          case None => deserializationError("Expected JsObject but found None")
      override def write[J](obj: PackageOption.JarManifest, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("m", obj.m)
        builder.endObject()

  private given mainClassFormat: JsonFormat[PackageOption.MainClass] =
    new JsonFormat[PackageOption.MainClass]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): PackageOption.MainClass =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val mainClassName = unbuilder.readField[String]("mainClassName")
            unbuilder.endObject()
            PackageOption.MainClass(mainClassName)
          case None => deserializationError("Expected JsObject but found None")
      override def write[J](obj: PackageOption.MainClass, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("mainClassName", obj.mainClassName)
        builder.endObject()

  private given manifestAttributesFormat: JsonFormat[PackageOption.ManifestAttributes] =
    new JsonFormat[PackageOption.ManifestAttributes]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): PackageOption.ManifestAttributes =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val attributes = unbuilder.readField[Vector[(String, String)]]("attributes")
            unbuilder.endObject()
            PackageOption.ManifestAttributes(attributes.map { case (k, v) =>
              Attributes.Name(k) -> v
            }: _*)
          case None => deserializationError("Expected JsObject but found None")
      override def write[J](obj: PackageOption.ManifestAttributes, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField(
          "attributes",
          obj.attributes.toVector.map { case (k, v) => k.toString -> v }
        )
        builder.endObject()

  private given fixedTimeStampFormat: JsonFormat[PackageOption.FixedTimestamp] =
    new JsonFormat[PackageOption.FixedTimestamp]:
      override def read[J](
          jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): PackageOption.FixedTimestamp =
        jsOpt match
          case Some(js) =>
            unbuilder.beginObject(js)
            val value = unbuilder.readField[Option[Long]]("value")
            unbuilder.endObject()
            PackageOption.FixedTimestamp(value)
          case None => deserializationError("Expected JsObject but found None")
      override def write[J](obj: PackageOption.FixedTimestamp, builder: Builder[J]): Unit =
        builder.beginObject()
        builder.addField("value", obj.value)
        builder.endObject()

  given JsonFormat[PackageOption] = flatUnionFormat4[
    PackageOption,
    PackageOption.JarManifest,
    PackageOption.MainClass,
    PackageOption.ManifestAttributes,
    PackageOption.FixedTimestamp,
  ]("type")
end PackageOption
