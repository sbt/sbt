/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.File
import java.net.URL
import sbt.internal.BuildDef.analyzed
import xsbti.FileConverter
import xsbt.api.{ Discovered, Discovery }
import xsbti.compile.CompileAnalysis
import sbt.internal.inc.ModuleUtilities

import sbt.io.IO
import scala.reflect.ClassTag

object PluginDiscovery:

  /**
   * Relative paths of resources that list top-level modules that are available.
   * Normally, the classes for those modules will be in the same classpath entry as the resource.
   */
  object Paths {
    final val AutoPlugins = "sbt/sbt.autoplugins"
    final val Builds = "sbt/sbt.builds"
  }

  /** Names of top-level modules that subclass sbt plugin-related classes: [[AutoPlugin]], and [[BuildDef]]. */
  final class DiscoveredNames(val autoPlugins: Seq[String], val builds: Seq[String]) {
    override def toString: String = s"""DiscoveredNames($autoPlugins, $builds)"""
  }

  def emptyDiscoveredNames: DiscoveredNames = new DiscoveredNames(Nil, Nil)

  /** Discovers and loads the sbt-plugin-related top-level modules from the classpath and source analysis in `data` and using the provided class `loader`. */
  def discoverAll(data: PluginData, loader: ClassLoader): DetectedPlugins = {
    def discover[A1: ClassTag](resource: String) =
      binarySourceModules[A1](data, loader, resource)
    import Paths._
    // TODO - Fix this once we can autodetect AutoPlugins defined by sbt itself.
    val defaultAutoPlugins = Seq(
      "sbt.plugins.IvyPlugin" -> sbt.plugins.IvyPlugin,
      "sbt.plugins.JvmPlugin" -> sbt.plugins.JvmPlugin,
      "sbt.plugins.CorePlugin" -> sbt.plugins.CorePlugin,
      "sbt.ScriptedPlugin" -> sbt.ScriptedPlugin,
      "sbt.plugins.SbtPlugin" -> sbt.plugins.SbtPlugin,
      "sbt.plugins.SemanticdbPlugin" -> sbt.plugins.SemanticdbPlugin,
      "sbt.plugins.JUnitXmlReportPlugin" -> sbt.plugins.JUnitXmlReportPlugin,
      "sbt.plugins.Giter8TemplatePlugin" -> sbt.plugins.Giter8TemplatePlugin,
      "sbt.plugins.MiniDependencyTreePlugin" -> sbt.plugins.MiniDependencyTreePlugin,
    )
    val detectedAutoPlugins = discover[AutoPlugin](AutoPlugins)
    val allAutoPlugins = (defaultAutoPlugins ++ detectedAutoPlugins.modules) map {
      case (name, value) =>
        DetectedAutoPlugin(name, value, sbt.Plugins.hasAutoImportGetter(value, loader))
    }
    new DetectedPlugins(allAutoPlugins, discover[BuildDef](Builds))
  }

  /** Discovers the sbt-plugin-related top-level modules from the provided source `analysis`. */
  def discoverSourceAll(analysis: CompileAnalysis): DiscoveredNames = {
    def discover[T](using classTag: reflect.ClassTag[T]): Seq[String] =
      sourceModuleNames(analysis, classTag.runtimeClass.getName)
    new DiscoveredNames(discover[AutoPlugin], discover[BuildDef])
  }

  // TODO: consider consolidating into a single file, which would make the classpath search 4x faster
  /** Writes discovered module `names` to zero or more files in `dir` as per [[writeDescriptor]] and returns the list of files written. */
  def writeDescriptors(names: DiscoveredNames, dir: File): Seq[File] = {
    import Paths._
    val files =
      writeDescriptor(names.autoPlugins, dir, AutoPlugins) ::
        writeDescriptor(names.builds, dir, Builds) ::
        Nil
    files.flatMap(_.toList)
  }

  /** Stores the module `names` in `dir / path`, one per line, unless `names` is empty and then the file is deleted and `None` returned. */
  def writeDescriptor(names: Seq[String], dir: File, path: String): Option[File] = {
    val descriptor: File = new File(dir, path)
    if (names.isEmpty) {
      IO.delete(descriptor)
      None
    } else {
      IO.writeLines(descriptor, names.distinct.sorted)
      Some(descriptor)
    }
  }

  /**
   * Discovers the names of top-level modules listed in resources named `resourceName` as per [[binaryModuleNames]] or
   * available as analyzed source and extending from any of `subclasses` as per [[sourceModuleNames]].
   */
  def binarySourceModuleNames(
      classpath: Def.Classpath,
      converter: FileConverter,
      loader: ClassLoader,
      resourceName: String,
      subclasses: String*
  ): Seq[String] =
    (
      binaryModuleNames(classpath, converter, loader, resourceName) ++
        analyzed(classpath, converter).flatMap(a => sourceModuleNames(a, subclasses*))
    ).distinct

  /** Discovers top-level modules in `analysis` that inherit from any of `subclasses`. */
  def sourceModuleNames(analysis: CompileAnalysis, subclasses: String*): Seq[String] = {
    val subclassSet = subclasses.toSet
    val defs = Tests.allDefs(analysis)
    val ds = Discovery(subclassSet, Set.empty)(defs)
    ds.flatMap {
      case (definition, Discovered(subs, _, _, true)) =>
        if ((subs & subclassSet).isEmpty) Nil else definition.name :: Nil
      case _ => Nil
    }
  }

  /**
   * Obtains the list of modules identified in all resource files `resourceName` from `loader` that are on `classpath`.
   * `classpath` and `loader` are both required to ensure that `loader`
   * doesn't bring in any resources outside of the intended `classpath`, such as from parent loaders.
   */
  def binaryModuleNames(
      classpath: Def.Classpath,
      converter: FileConverter,
      loader: ClassLoader,
      resourceName: String
  ): Seq[String] =
    import scala.jdk.CollectionConverters.*
    loader
      .getResources(resourceName)
      .asScala
      .toSeq
      .filter(onClasspath(classpath, converter))
      .flatMap { u =>
        IO.readLinesURL(u).map(_.trim).filter(!_.isEmpty)
      }

  /** Returns `true` if `url` is an entry in `classpath`. */
  def onClasspath(classpath: Def.Classpath, converter: FileConverter)(url: URL): Boolean =
    val cpFiles = classpath.map(_.data).map(converter.toPath).map(_.toFile)
    IO.urlAsFile(url) exists (cpFiles.contains)

  private[sbt] def binarySourceModules[A: ClassTag](
      data: PluginData,
      loader: ClassLoader,
      resourceName: String
  ): DetectedModules[A] =
    val classpath = data.classpath
    val classTag = summon[ClassTag[A]]
    val namesAndValues =
      if classpath.isEmpty then Nil
      else
        val names =
          binarySourceModuleNames(
            classpath,
            data.converter,
            loader,
            resourceName,
            classTag.runtimeClass.getName
          )
        loadModules[A](data, names, loader)
    DetectedModules(namesAndValues)

  private def loadModules[A: reflect.ClassTag](
      data: PluginData,
      names: Seq[String],
      loader: ClassLoader
  ): Seq[(String, A)] =
    try ModuleUtilities.getCheckedObjects[A](names, loader)
    catch {
      case e: ExceptionInInitializerError =>
        val cause = e.getCause
        if (cause eq null) throw e else throw cause
      case e: LinkageError => incompatiblePlugins(data, e)
    }

  private def incompatiblePlugins(data: PluginData, t: LinkageError): Nothing = {
    val evicted = data.report.toList.flatMap(_.configurations.flatMap(_.evicted))
    val evictedModules = evicted.map { id =>
      (id.organization, id.name)
    }.distinct
    val evictedStrings = evictedModules map { case (o, n) => o + ":" + n }
    val msgBase = "Binary incompatibility in plugins detected."
    val msgExtra =
      if (evictedStrings.isEmpty) ""
      else
        "\nNote that conflicts were resolved for some dependencies:\n\t" + evictedStrings.mkString(
          "\n\t"
        )
    throw new IncompatiblePluginsException(msgBase + msgExtra, t)
  }
end PluginDiscovery
