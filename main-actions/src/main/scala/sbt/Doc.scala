/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.inc.{ AnalyzingCompiler, PlainVirtualFile }
import sbt.internal.util.ManagedLogger
import sbt.util.CacheStoreFactory
import sbt.util.Logger
import xsbti.Reporter
import xsbti.compile.JavaTools

object Doc {
  import RawCompileLike._

  def scaladoc(
      label: String,
      cacheStoreFactory: CacheStoreFactory,
      compiler: AnalyzingCompiler
  ): Gen =
    scaladoc(label, cacheStoreFactory, compiler, Seq())

  def scaladoc(
      label: String,
      cacheStoreFactory: CacheStoreFactory,
      compiler: AnalyzingCompiler,
      fileInputOptions: Seq[String]
  ): Gen =
    cached(
      cacheStoreFactory,
      fileInputOptions,
      prepare(
        label + " Scala API documentation",
        (sources, classpath, outputDirectory, options, maxErrors, log) => {
          compiler.doc(
            sources map { x =>
              PlainVirtualFile(x.toPath)
            },
            classpath map { x =>
              PlainVirtualFile(x.toPath)
            },
            outputDirectory.toPath,
            options,
            maxErrors,
            log
          )
        }
      )
    )

  @deprecated("Going away", "1.1.1")
  def javadoc(
      label: String,
      cacheStoreFactory: CacheStoreFactory,
      doc: JavaTools,
      log: Logger,
      reporter: Reporter,
  ): Gen = ???

  @deprecated("Going away", "1.1.1")
  def javadoc(
      label: String,
      cacheStoreFactory: CacheStoreFactory,
      doc: JavaTools,
      log: Logger,
      reporter: Reporter,
      fileInputOptions: Seq[String],
  ): Gen = ???

  @deprecated("Going away", "1.1.1")
  val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")
}

@deprecated("Going away", "1.1.1")
sealed trait Doc {
  @deprecated("Going away", "1.1.1")
  type Gen = (Seq[File], Seq[File], File, Seq[String], Int, ManagedLogger) => Unit
}
