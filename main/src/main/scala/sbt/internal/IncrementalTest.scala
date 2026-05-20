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
    // TODO: Potentially do something about JUnit 5 and others which might not use class name
    Map((testNames.flatMap: name =>
      stamper.transitiveStamp(name, extra ++ rds ++ opts, s.log) match
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

  private val stamps = mutable.Map.empty[String, Set[Digest]]
  // Cached so by-name `analyses0` is only evaluated once
  private lazy val analyses = analyses0
  private val stampVf: VirtualFileRef => Digest =
    CacheImplicits.virtualFileRefToDigest(_)(converter)

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
    val digests = transitiveStamps(javaClassName, extraHashes, log)
    if digests.nonEmpty then Some(Digest.sha256Hash(digests*))
    else None

  private def transitiveStamps(
      javaClassName: String,
      extraHashes: Seq[Digest],
      log: Logger,
  ): Seq[Digest] =
    val builder = Set.newBuilder[Digest]
    analyses.foreach(internalStamp(builder, javaClassName, _, mutable.Set.empty, log))
    val digests = builder.result().toSeq.sorted
    digests ++ extraHashes

  private def internalStamp(
      builder: mutable.Builder[Digest, Set[Digest]],
      javaClassName: String,
      analysis: Analysis,
      alreadySeen: mutable.Set[String],
      log: Logger,
  ): Unit =
    import analysis.relations

    // log.debug(s"test: internalStamp($javaClassName)")
    def internalStamp0(className: String): Unit =
      // Use a new builder so we can cache the result in `stamps`
      val newBuilder = Set.newBuilder[Digest]

      // Zinc doesn't fully track the transitive dependencies
      relations
        .internalClassDeps(className)
        .foreach: otherCN =>
          internalStamp(newBuilder, otherCN, analysis, alreadySeen, log)
      // log.debug(s"  internalStamp: internalDeps: $className = $internalDeps")
      relations
        .externalDeps(className)
        .foreach: libClassName =>
          newBuilder ++= transitiveStamps(libClassName, Nil, log)
      relations
        .externalDeps(className)
        .foreach: libClassName =>
          relations.libraryClassName
            .reverse(libClassName)
            .foreach: vf =>
              newBuilder += stampVf(vf)
      analysis.apis.internal
        .get(className)
        .toSet
        .foreach: analyzed =>
          newBuilder += Digest.dummy(
            37 * (17 + analyzed.transitiveBytecodeHash) + analyzed.bytecodeHash
          )

      val xs = newBuilder.result()
      if xs.nonEmpty then stamps(className) = xs
      else ()

      builder ++= xs

    if alreadySeen.contains(javaClassName) then ()
    else
      stamps.get(javaClassName) match
        case Some(xs) => builder ++= xs
        case _        =>
          alreadySeen += javaClassName
          // Note: internalClassDeps uses Scala-encoded class name for companion objects
          val classNames = relations.productClassName.reverse(javaClassName)
          classNames.foreach(internalStamp0)
end ClassStamper
