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
import java.util.concurrent.ConcurrentHashMap
import Keys.{ test, compileInputs, fileConverter, fullClasspath, streams }
import sbt.Def.Initialize
import sbt.internal.inc.Analysis
import sbt.internal.util.Attributed
import sbt.internal.util.Types.const
import sbt.io.syntax.*
import sbt.io.{ GlobFilter, IO, NameFilter }
import sbt.protocol.testing.TestResult
import sbt.SlashSyntax0.*
import sbt.util.Digest
import sbt.util.CacheImplicits.given
import scala.collection.concurrent
import scala.collection.mutable
import scala.collection.SortedSet
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFileRef }

object IncrementalTest:
  def filterTask: Initialize[Task[Seq[String] => Seq[String => Boolean]]] =
    Def.task {
      val cp = (Keys.test / fullClasspath).value
      val s = (Keys.test / streams).value
      val digests = (Keys.definedTestDigests).value
      val succeeded = TestStatus.read(succeededFile(s.cacheDirectory))
      def hasSucceeded(className: String): Boolean = succeeded.get(className) match
        case None     => false
        case Some(ts) => Some(ts) == digests.get(className)
      args =>
        for filter <- selectedFilter(args)
        yield (test: String) => filter(test) && !hasSucceeded(test)
    }

  // cache the test digests against the fullClasspath.
  def definedTestDigestTask: Initialize[Task[Map[String, Digest]]] = Def.cachedTask {
    val cp = (Keys.test / fullClasspath).value
    val testNames = Keys.definedTests.value.map(_.name).toVector.distinct
    val converter = fileConverter.value
    val inputs = Keys.compileInputs.value
    val extra = Digest(converter.toVirtualFile(inputs.options.classesDirectory))
    val stamper = ClassStamper(cp, converter)
    // TODO: Potentially do something about JUnit 5 and others which might not use class name
    Map((testNames.flatMap: name =>
      stamper.transitiveStamp(name, Vector(extra)) match
        case Some(ts) => Seq(name -> ts)
        case None     => Nil
    ): _*)
  }

  def succeededFile(dir: File): File = dir / "succeeded_tests.txt"

  def selectedFilter(args: Seq[String]): Seq[String => Boolean] =
    def matches(nfs: Seq[NameFilter], s: String) = nfs.exists(_.accept(s))
    val (excludeArgs, includeArgs) = args.partition(_.startsWith("-"))
    val includeFilters = includeArgs.map(GlobFilter.apply)
    val excludeFilters = excludeArgs.map(_.substring(1)).map(GlobFilter.apply)
    (includeFilters, excludeArgs) match
      case (Nil, Nil) => Seq(const(true))
      case (Nil, _)   => Seq((s: String) => !matches(excludeFilters, s))
      case _ =>
        includeFilters.map(f => (s: String) => (f.accept(s) && !matches(excludeFilters, s)))
end IncrementalTest

// Assumes exclusive ownership of the file.
private[sbt] class TestStatusReporter(
    f: File,
    digests: Map[String, Digest],
) extends TestsListener:
  private lazy val succeeded: concurrent.Map[String, Digest] =
    TestStatus.read(f)

  def doInit(): Unit = ()
  def startGroup(name: String): Unit =
    succeeded.remove(name)
    ()
  def testEvent(event: TestEvent): Unit = ()
  def endGroup(name: String, t: Throwable): Unit = ()

  /**
   * If the test has succeeded, record the fact that it has
   * using its unique digest, so we can skip the test later.
   */
  def endGroup(name: String, result: TestResult): Unit =
    if result == TestResult.Passed then
      digests.get(name) match
        case Some(ts) => succeeded(name) = ts
        case None     => succeeded(name) = Digest.zero
    else ()
  def doComplete(finalResult: TestResult): Unit =
    TestStatus.write(succeeded, "Successful Tests", f)
end TestStatusReporter

private[sbt] object TestStatus:
  import java.util.Properties
  def read(f: File): concurrent.Map[String, Digest] =
    import scala.jdk.CollectionConverters.*
    val props = Properties()
    IO.load(props, f)
    val result = ConcurrentHashMap[String, Digest]()
    props.asScala.iterator.foreach { case (k, v) => result.put(k, Digest(v)) }
    result.asScala

  def write(map: collection.Map[String, Digest], label: String, f: File): Unit =
    IO.writeLines(
      f,
      s"# $label" ::
        map.toList.sortBy(_._1).map { case (k, v) =>
          s"$k=$v"
        }
    )
end TestStatus

/**
 * ClassStamper provides `transitiveStamp` method to calculate a unique
 * fingerprint, which will be used for runtime invalidation.
 */
class ClassStamper(
    classpath: Seq[Attributed[HashedVirtualFileRef]],
    converter: FileConverter,
):
  private val stamps = mutable.Map.empty[String, SortedSet[Digest]]
  private val vfStamps = mutable.Map.empty[VirtualFileRef, Digest]
  private lazy val analyses = classpath
    .flatMap(a => BuildDef.extractAnalysis(a.metadata, converter))
    .collect { case analysis: Analysis => analysis }

  /**
   * Given a classpath and a class name, this tries to create a SHA-256 digest.
   * @param className className to stamp
   * @param extraHashes additional information to include into the returning digest
   */
  private[sbt] def transitiveStamp(className: String, extaHashes: Seq[Digest]): Option[Digest] =
    val digests = SortedSet(analyses.flatMap(internalStamp(className, _, Set.empty)): _*)
    if digests.nonEmpty then Some(Digest.sha256Hash(digests.toSeq ++ extaHashes: _*))
    else None

  private def internalStamp(
      className: String,
      analysis: Analysis,
      alreadySeen: Set[String],
  ): SortedSet[Digest] =
    if alreadySeen.contains(className) then SortedSet.empty
    else
      stamps.get(className) match
        case Some(xs) => xs
        case _ =>
          import analysis.relations
          val internalDeps = relations
            .internalClassDeps(className)
            .flatMap: otherCN =>
              internalStamp(otherCN, analysis, alreadySeen + className)
          val internalJarDeps = relations
            .externalDeps(className)
            .flatMap: libClassName =>
              transitiveStamp(libClassName, Nil)
          val externalDeps = relations
            .externalDeps(className)
            .flatMap: libClassName =>
              relations.libraryClassName
                .reverse(libClassName)
                .map(stampVf)
          val classDigests = relations.productClassName
            .reverse(className)
            .flatMap: prodClassName =>
              relations
                .definesClass(prodClassName)
                .flatMap: sourceFile =>
                  relations
                    .products(sourceFile)
                    .map(stampVf)
          // TODO: substitue the above with
          // val classDigests = relations.productClassName
          //   .reverse(className)
          //   .flatMap: prodClassName =>
          //     analysis.apis.internal
          //       .get(prodClassName)
          //       .map: analyzed =>
          //         0L // analyzed.??? we need a hash here
          val xs = SortedSet(
            (internalDeps union internalJarDeps union externalDeps union classDigests).toSeq: _*
          )
          if xs.nonEmpty then stamps(className) = xs
          else ()
          xs
  def stampVf(vf: VirtualFileRef): Digest =
    vf match
      case h: HashedVirtualFileRef => Digest(h)
      case _ =>
        vfStamps.getOrElseUpdate(vf, Digest.sha256Hash(converter.toPath(vf)))
end ClassStamper
