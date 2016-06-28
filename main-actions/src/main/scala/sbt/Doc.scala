/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah, Indrajit Raychaudhuri
 */
package sbt

import java.io.{ File, PrintWriter }
import sbt.internal.inc.AnalyzingCompiler

import Predef.{ conforms => _, _ }
import sbt.internal.util.Types.:+:
import sbt.io.syntax._
import sbt.io.IO

import sbinary.DefaultProtocol.FileFormat
import sbt.internal.util.Cache.{ defaultEquiv, hConsCache, hNilCache, seqCache, seqFormat, streamFormat, StringFormat, UnitFormat, wrapIn }
import sbt.internal.util.Tracked.{ inputChanged, outputChanged }
import sbt.internal.util.{ FilesInfo, HashFileInfo, HNil, ModifiedFileInfo, PlainFileInfo }
import sbt.internal.util.FilesInfo.{ exists, hash, lastModified }
import xsbti.Reporter
import xsbti.compile.JavaTools

import sbt.util.Logger

object Doc {
  import RawCompileLike._
  def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler): Gen =
    scaladoc(label, cache, compiler, Seq())
  def scaladoc(label: String, cache: File, compiler: AnalyzingCompiler, fileInputOptions: Seq[String]): Gen =
    cached(cache, fileInputOptions, prepare(label + " Scala API documentation", compiler.doc))
  def javadoc(label: String, cache: File, doc: JavaTools, log: Logger, reporter: Reporter): Gen =
    javadoc(label, cache, doc, log, reporter, Seq())
  def javadoc(label: String, cache: File, doc: JavaTools, log: Logger, reporter: Reporter, fileInputOptions: Seq[String]): Gen =
    cached(cache, fileInputOptions, prepare(label + " Java API documentation", filterSources(javaSourcesOnly,
      (sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maxErrors: Int, log: Logger) => {
        // doc.doc
        ???
      })))

  val javaSourcesOnly: File => Boolean = _.getName.endsWith(".java")

  // @deprecated("Use `scaladoc`", "0.13.0")
  // def apply(maximumErrors: Int, compiler: AnalyzingCompiler) = new Scaladoc(maximumErrors, compiler)

  // @deprecated("Use `javadoc`", "0.13.0")
  // def apply(maximumErrors: Int, compiler: sbt.compiler.Javadoc) = new Javadoc(maximumErrors, compiler)

  private[sbt] final class Scaladoc(maximumErrors: Int, compiler: AnalyzingCompiler) extends Doc {
    def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger): Unit = {
      generate("Scala", label, compiler.doc, sources, classpath, outputDirectory, options, maximumErrors, log)
    }
  }

  // private[sbt] final class Javadoc(maximumErrors: Int, doc: sbt.internal.inc.Javadoc) extends Doc {
  //   def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger) {
  //     // javadoc doesn't handle *.scala properly, so we evict them from javadoc sources list.
  //     generate("Java", label, doc.doc, sources.filterNot(_.name.endsWith(".scala")), classpath, outputDirectory, options, maximumErrors, log)
  //   }
  // }
}
// @deprecated("No longer used.  See `Doc.javadoc` or `Doc.scaladoc`", "0.13.0")
sealed trait Doc {
  type Gen = (Seq[File], Seq[File], File, Seq[String], Int, Logger) => Unit

  //   def apply(label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger): Unit

  private[sbt] final def generate(variant: String, label: String, docf: Gen, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], maxErrors: Int, log: Logger): Unit = {
    val logSnip = variant + " API documentation"
    if (sources.isEmpty)
      log.info("No sources available, skipping " + logSnip + "...")
    else {
      log.info("Generating " + logSnip + " for " + label + " sources to " + outputDirectory.absolutePath + "...")
      IO.delete(outputDirectory)
      IO.createDirectory(outputDirectory)
      docf(sources, classpath, outputDirectory, options, maxErrors, log)
      log.info(logSnip + " generation successful.")
    }
  }

  //   def cached(cache: File, label: String, sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String], log: Logger) {
  //     type Inputs = FilesInfo[HashFileInfo] :+: FilesInfo[ModifiedFileInfo] :+: String :+: File :+: Seq[String] :+: HNil
  //     val inputs: Inputs = hash(sources.toSet) :+: lastModified(classpath.toSet) :+: classpath.absString :+: outputDirectory :+: options :+: HNil
  //     implicit val stringEquiv: Equiv[String] = defaultEquiv
  //     implicit val fileEquiv: Equiv[File] = defaultEquiv
  //     val cachedDoc = inputChanged(cache / "inputs") { (inChanged, in: Inputs) =>
  //       outputChanged(cache / "output") { (outChanged, outputs: FilesInfo[PlainFileInfo]) =>
  //         if (inChanged || outChanged)
  //           apply(label, sources, classpath, outputDirectory, options, log)
  //         else
  //           log.debug("Doc uptodate: " + outputDirectory.getAbsolutePath)
  //       }
  //     }
  //     cachedDoc(inputs)(() => exists(outputDirectory.allPaths.get.toSet))
  //   }
}
