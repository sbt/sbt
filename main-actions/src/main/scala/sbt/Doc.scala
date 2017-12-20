/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import sbt.internal.inc.AnalyzingCompiler

import Predef.{ conforms => _, _ }
import sbt.io.syntax._
import sbt.io.IO

import sbt.util.CacheStoreFactory
import xsbti.Reporter
import xsbti.compile.JavaTools

import sbt.util.Logger
import sbt.internal.util.ManagedLogger

object Doc {
  import RawCompileLike._
  def scaladoc(label: String,
               cacheStoreFactory: CacheStoreFactory,
               compiler: AnalyzingCompiler): Gen =
    scaladoc(label, cacheStoreFactory, compiler, Seq())
  def scaladoc(label: String,
               cacheStoreFactory: CacheStoreFactory,
               compiler: AnalyzingCompiler,
               fileInputOptions: Seq[String]): Gen =
    cached(cacheStoreFactory,
           fileInputOptions,
           prepare(label + " Scala API documentation", compiler.doc))
  def javadoc(label: String,
              cacheStoreFactory: CacheStoreFactory,
              doc: JavaTools,
              log: Logger,
              reporter: Reporter): Gen =
    javadoc(label, cacheStoreFactory, doc, log, reporter, Seq())
  def javadoc(label: String,
              cacheStoreFactory: CacheStoreFactory,
              doc: JavaTools,
              log: Logger,
              reporter: Reporter,
              fileInputOptions: Seq[String]): Gen =
    cached(
      cacheStoreFactory,
      fileInputOptions,
      prepare(
        label + " Java API documentation",
        filterSources(
          javaSourcesOnly,
          (sources: Seq[File],
           classpath: Seq[File],
           outputDirectory: File,
           options: Seq[String],
           maxErrors: Int,
           log: Logger) => {
            // doc.doc
            ???
          }
        )
      )
    )

  val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")

  private[sbt] final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler) extends Doc {
    def apply(label: String,
              sources: Seq[File],
              classpath: Seq[File],
              outputDirectory: File,
              options: Seq[String],
              log: ManagedLogger): Unit = {
      generate("Scala",
               label,
               compiler.doc,
               sources,
               classpath,
               outputDirectory,
               options,
               maximumErrors,
               log)
    }
  }
}

sealed trait Doc {
  type Gen = (Seq[File], Seq[File], File, Seq[String], Int, ManagedLogger) => Unit

  private[sbt] final def generate(variant: String,
                                  label: String,
                                  docf: Gen,
                                  sources: Seq[File],
                                  classpath: Seq[File],
                                  outputDirectory: File,
                                  options: Seq[String],
                                  maxErrors: Int,
                                  log: ManagedLogger): Unit = {
    val logSnip = variant + " API documentation"
    if (sources.isEmpty)
      log.info("No sources available, skipping " + logSnip + "...")
    else {
      log.info(
        "Generating " + logSnip + " for " + label + " sources to " + outputDirectory.absolutePath + "...")
      IO.delete(outputDirectory)
      IO.createDirectory(outputDirectory)
      docf(sources, classpath, outputDirectory, options, maxErrors, log)
      log.info(logSnip + " generation successful.")
    }
  }
}
