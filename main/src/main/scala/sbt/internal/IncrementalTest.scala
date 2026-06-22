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
import Keys.{ fileConverter, fullClasspath, streams }
import sbt.Def.Initialize
import sbt.internal.inc.Analysis
import sbt.internal.util.Attributed
import sbt.internal.util.Types.const
import sbt.io.{ GlobFilter, IO, NameFilter }
import sbt.protocol.testing.TestResult
import sbt.util.{ ActionCache, BuildWideCacheConfiguration, CacheLevelTag, Digest, Logger }
import sbt.util.CacheImplicits
import sbt.util.CacheImplicits.given
import scala.collection.concurrent
import scala.collection.mutable
import xsbti.{ FileConverter, HashedVirtualFileRef, VirtualFileRef }

object IncrementalTest:
  def filterTask: Initialize[Task[Seq[String] => Seq[String => Boolean]]] =
    Def.task {
      val cp = (Keys.test / fullClasspath).value
      val digests = (Keys.definedTestDigests).value
      val config = Def.cacheConfiguration.value
      def hasCachedSuccess(ts: Digest, options: Seq[String]): Boolean =
        val input = cacheInput(ts, options)
        ActionCache.exists(input._1, input._2, input._3, config)
      def hasSucceeded(className: String, options: Seq[String]): Boolean =
        digests.get(className) match
          case None     => false
          case Some(ts) => hasCachedSuccess(ts, options)
      args =>
        val (pattern, options) =
          args.indexOf("--") match
            case idx if idx >= 0 =>
              val (s1, s2) = args.splitAt(idx)
              (s1, s2.drop(1))
            case _ => (args, Nil)
        for filter <- selectedFilter(pattern)
        yield (test: String) => filter(test) && !hasSucceeded(test, options)
    }

  // cache the test digests against the fullClasspath.
  def definedTestDigestTask: Initialize[Task[Map[String, Digest]]] = Def.cachedTask {
    val s = (Keys.test / streams).value
    val cp = (Keys.test / fullClasspath).value
    val testNames = Keys.definedTests.value.map(_.name).toVector.distinct
    val opts = (Keys.test / Keys.testOptionDigests).value
    val converter = fileConverter.value
    val rds = Keys.resourceDigests.value
    val extra = Keys.extraTestDigests.value
    val stamper = ClassStamper(cp, converter)
    val testDigestExtra = extra ++ rds ++ opts
    // TODO: Potentially do something about JUnit 5 and others which might not use class name
    Map((testNames.flatMap: name =>
      stamper.transitiveStamp(name, testDigestExtra, s.log) match
        case Some(ts) => Seq(name -> ts)
        case None     => Nil
    )*)
  }

  def extraTestDigestsTask: Initialize[Task[Seq[Digest]]] = Def.cachedTask {
    // by default this captures JVM version
    val extraInc = Keys.extraIncOptions.value
    // throw in any information useful for runtime invalidation
    val salt = s"""${extraInc.mkString(",")}
"""
    Vector(Digest.sha256Hash(salt.getBytes("UTF-8")))
  }

  /** Expands `...` to `**` in glob patterns. */
  def expandGlob(pattern: String): String = pattern.replace("...", "**")

  def selectedFilter(args: Seq[String]): Seq[String => Boolean] =
    def matches(nfs: Seq[NameFilter], s: String) = nfs.exists(_.accept(s))
    val (excludeArgs, includeArgs) = args.partition(_.startsWith("-"))
    val includeFilters = includeArgs.map(expandGlob).map(GlobFilter.apply)
    val excludeFilters = excludeArgs.map(_.substring(1)).map(expandGlob).map(GlobFilter.apply)
    (includeFilters, excludeArgs) match
      case (Nil, Nil) => Seq(const(true))
      case (Nil, _)   => Seq((s: String) => !matches(excludeFilters, s))
      case _          =>
        includeFilters.map(f => (s: String) => (f.accept(s) && !matches(excludeFilters, s)))

  private[sbt] def cacheInput(
      value: Digest,
      frameworkOptions: Seq[String]
  ): (Seq[String], Digest, Digest) = (frameworkOptions, value, Digest.zero)

end IncrementalTest

private[sbt] case class TestStatusReporter(
    digests: Map[String, Digest],
    cacheConfiguration: BuildWideCacheConfiguration,
) extends TestsListener:
  // int value to represent success
  private final val successfulTest = 0
  private var _args: Seq[String] = Nil
  def getArgs: Seq[String] = _args
  def setArguments(args: Seq[String]): Unit =
    _args = args
  def doInit(): Unit = ()
  def startGroup(name: String): Unit = ()
  def testEvent(event: TestEvent): Unit = ()
  def endGroup(name: String, t: Throwable): Unit = ()

  /**
   * If the test has succeeded, record the fact that it has
   * using its unique digest, so we can skip the test later.
   */
  def endGroup(name: String, result: TestResult): Unit =
    if result == TestResult.Passed then
      digests.get(name) match
        case Some(ts) =>
          // treat each test suite as a successful action that returns 0
          val input = IncrementalTest.cacheInput(ts, getArgs)
          ActionCache.cache(
            key = input._1,
            codeContentHash = input._2,
            extraHash = input._3,
            tags = CacheLevelTag.all.toList,
            config = cacheConfiguration,
          ): (_) =>
            ActionCache.actionResult(successfulTest)
        case None => ()
    else ()
  def doComplete(finalResult: TestResult): Unit = ()
end TestStatusReporter

private[sbt] object TestStatus:
  import java.util.Properties
  def read(f: File): concurrent.Map[String, Digest] =
    import scala.jdk.CollectionConverters.*
    val props = Properties()
    IO.load(props, f)
    val result = ConcurrentHashMap[String, Digest]()
    props.asScala.iterator.foreach { (k, v) => result.put(k, Digest(v)) }
    result.asScala

  def write(map: collection.Map[String, Digest], label: String, f: File): Unit =
    IO.writeLines(
      f,
      s"# $label" ::
        map.toList.sortBy(_._1).map { (k, v) =>
          s"$k=$v"
        }
    )
end TestStatus

/**
 * ClassStamper provides `transitiveStamp` method to calculate a unique
 * fingerprint, which will be used for runtime invalidation.
 */
class ClassStamper private[sbt] (
    analyses0: => Seq[Analysis],
    converter: FileConverter,
):
  def this(
      classpath: Seq[Attributed[HashedVirtualFileRef]],
      converter: FileConverter,
  ) =
    this(
      classpath
        .flatMap(a => BuildDef.extractAnalysis(a.metadata, converter))
        .collect { case analysis: Analysis => analysis },
      converter,
    )

  // Leaf digests (class bytecode hashes + library file digests) are interned to dense
  // ints so transitive digest sets can be held as bit sets: union is a word-parallel OR
  // and each member costs one bit instead of a 32-byte Digest.
  private val digestIds = mutable.HashMap.empty[Digest, Int]
  private val digestList = mutable.ArrayBuffer.empty[Digest]
  private def idOf(d: Digest): Int =
    digestIds.getOrElseUpdate(d, { val i = digestList.size; digestList += d; i })

  private val stamps = mutable.Map.empty[String, mutable.BitSet]
  // Memoizes the full transitive digest set per class name (excluding extraHashes), so the
  // re-entrant external-dep walk isn't recomputed for every reference. Mapping bits back to
  // digests and sorting is deferred to the root (`transitiveStamp`); intermediate results
  // are only ever OR-ed into another bit set, where order and identity are irrelevant.
  private val transitiveCache = mutable.Map.empty[String, mutable.BitSet]
  // Cached so by-name `analyses0` is only evaluated once
  private lazy val analyses = analyses0
  // Index of binary class name -> analyses that produce it, so a stamp can dispatch
  // straight to its owning analyses instead of scanning every analysis on the classpath.
  private lazy val analysesByProduct: Map[String, Seq[Analysis]] =
    val acc = mutable.HashMap.empty[String, mutable.ListBuffer[Analysis]]
    analyses.foreach: a =>
      a.relations.productClassName._2s.foreach: bin =>
        acc.getOrElseUpdate(bin, mutable.ListBuffer.empty) += a
    acc.iterator.map((k, v) => k -> v.toSeq).toMap
  // Memoized: virtualFileRefToDigest does a filesystem stat per call, and the same
  // library ref is referenced by many classes.
  private val vfDigests = mutable.Map.empty[VirtualFileRef, Digest]
  private def stampVf(vf: VirtualFileRef): Digest =
    vfDigests.getOrElseUpdate(vf, CacheImplicits.virtualFileRefToDigest(vf)(converter))

  /**
   * Given a classpath and a class name, this tries to create a SHA-256 digest.
   * @param javaClassName Java-enclded class name to stamp
   * @param extraHashes additional information to include into the returning digest
   */
  private[sbt] def transitiveStamp(
      javaClassName: String,
      extraHashes: Seq[Digest],
      log: Logger,
  ): Option[Digest] =
    val digests = sortedDigests(transitiveStamps(javaClassName, log)) ++ extraHashes
    if digests.nonEmpty then Some(Digest.sha256Hash(digests*))
    else None

  // Map a bit set back to its digests
  private def sortedDigests(bits: mutable.BitSet): Seq[Digest] =
    val buf = mutable.ArrayBuffer.empty[Digest]
    bits.foreach(i => buf += digestList(i))
    buf.sortInPlace()
    buf.toSeq

  private def transitiveStamps(
      javaClassName: String,
      log: Logger,
  ): mutable.BitSet =
    transitiveCache.getOrElseUpdate(
      javaClassName, {
        val builder = mutable.BitSet.empty
        analysesByProduct
          .getOrElse(javaClassName, Nil)
          .foreach(internalStamp(builder, javaClassName, _, mutable.Set.empty, log))
        builder
      }
    )

  private def internalStamp(
      builder: mutable.BitSet,
      javaClassName: String,
      analysis: Analysis,
      alreadySeen: mutable.Set[String],
      log: Logger,
  ): Unit =
    import analysis.relations

    // log.debug(s"test: internalStamp($javaClassName)")
    def internalStamp0(className: String): Unit =
      // Use a new bit set so we can cache the result in `stamps`
      val newBuilder = mutable.BitSet.empty

      // Zinc doesn't fully track the transitive dependencies
      relations
        .internalClassDeps(className)
        .foreach: otherCN =>
          internalStamp(newBuilder, otherCN, analysis, alreadySeen, log)
      // log.debug(s"  internalStamp: internalDeps: $className = $internalDeps")
      relations
        .externalDeps(className)
        .foreach: libClassName =>
          newBuilder |= transitiveStamps(libClassName, log)
          relations.libraryClassName
            .reverse(libClassName)
            .foreach: vf =>
              newBuilder += idOf(stampVf(vf))
      analysis.apis.internal
        .get(className)
        .foreach: analyzed =>
          newBuilder += idOf(
            Digest.dummy(37 * (17 + analyzed.transitiveBytecodeHash) + analyzed.bytecodeHash)
          )

      if newBuilder.nonEmpty then stamps(className) = newBuilder
      else ()

      builder |= newBuilder

    if alreadySeen.contains(javaClassName) then ()
    else
      stamps.get(javaClassName) match
        case Some(xs) => builder |= xs
        case _        =>
          alreadySeen += javaClassName
          // Note: internalClassDeps uses Scala-encoded class name for companion objects
          val classNames = relations.productClassName.reverse(javaClassName)
          classNames.foreach(internalStamp0)
end ClassStamper
