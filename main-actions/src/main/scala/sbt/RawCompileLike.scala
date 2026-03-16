/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.io.syntax.*
import sbt.io.IO
import sbt.internal.inc.{ RawCompiler, ScalaInstance }
import sbt.util.{ CacheStoreFactory, Tracked }
import sbt.internal.util.ManagedLogger
import xsbti.compile.ClasspathOptions

object RawCompileLike {
  type Gen = (Seq[File], Seq[File], File, Seq[String], Int, ManagedLogger) => Unit

  def cached(cacheStoreFactory: CacheStoreFactory, doCompile: Gen): Gen =
    cached(cacheStoreFactory, Seq(), doCompile)

  def cached(
      cacheStoreFactory: CacheStoreFactory,
      fileInputOpts: Seq[String],
      doCompile: Gen
  ): Gen =
    (sources, classpath, outputDirectory, options, maxErrors, log) =>
      Tracked.cachedTransform(
        cacheStoreFactory,
        sources,
        classpath,
        outputDirectory,
        options,
        fileInputOpts,
        log,
      ) {
        doCompile(sources, classpath, outputDirectory, options, maxErrors, log)
      }

  def prepare(description: String, doCompile: Gen): Gen =
    (sources, classpath, outputDirectory, options, maxErrors, log) => {
      if (sources.isEmpty) log.info("No sources available, skipping " + description + "...")
      else {
        log.info(description.capitalize + " to " + outputDirectory.absolutePath + "...")
        IO.delete(outputDirectory)
        IO.createDirectory(outputDirectory)
        doCompile(sources, classpath, outputDirectory, options, maxErrors, log)
        log.info(description.capitalize + " successful.")
      }
    }

  def filterSources(f: File => Boolean, doCompile: Gen): Gen =
    (sources, classpath, outputDirectory, options, maxErrors, log) =>
      doCompile(sources filter f, classpath, outputDirectory, options, maxErrors, log)

  def rawCompile(instance: ScalaInstance, cpOptions: ClasspathOptions): Gen =
    (sources, classpath, outputDirectory, options, _, log) => {
      val compiler = new RawCompiler(instance, cpOptions, log)
      compiler(sources.map(_.toPath), classpath.map(_.toPath), outputDirectory.toPath, options)
    }

  def compile(
      label: String,
      cacheStoreFactory: CacheStoreFactory,
      instance: ScalaInstance,
      cpOptions: ClasspathOptions
  ): Gen =
    cached(cacheStoreFactory, prepare(label + " sources", rawCompile(instance, cpOptions)))

  val nop: Gen = (_, _, _, _, _, _) => ()
}
