/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.File
import java.time.{ Instant, ZoneOffset, ZonedDateTime }
import java.util.{ Calendar, GregorianCalendar }
import hedgehog.*
import _root_.sbt.io.IO
import _root_.sbt.librarymanagement.*

/**
 * Hedgehog generators for `UpdateReport`s shaped like the resolver's: organizations interleave in
 * module order, evicted modules appear only in details, and callers include sbt's artificial ones.
 * Artifact files are not created.
 */
object GenUpdateReport:
  val artifactBase: File = new File(IO.temporaryDirectory, "gen-update-report")

  val genOrg: Gen[String] =
    Gen.element1("org.a", "org.b", "org.c")
  val genName: Gen[String] = Gen.element1("x", "y", "z")
  val genVersion: Gen[String] = Gen.element1("1.0.0", "1.1.0", "2.0.0")
  val genConfigName: Gen[String] = Gen.element1("compile", "runtime", "test", "provided")
  val genWord: Gen[String] = Gen.string(Gen.alpha, Range.linear(1, 8))

  def genModuleID: Gen[ModuleID] =
    for
      org <- genOrg
      name <- genName
      version <- genVersion
    yield ModuleID(org, name, version)

  def genCaller: Gen[Caller] =
    for
      org <- Gen.frequency1(
        3 -> genOrg,
        1 -> Gen.constant(JsonUtil.sbtOrgTemp),
        1 -> Gen.constant(JsonUtil.fakeCallerOrganization),
      )
      name <- genName
      version <- genVersion
      force <- Gen.boolean
      transitive <- Gen.boolean
    yield Caller(
      ModuleID(org, name, version),
      Vector(ConfigRef("compile")),
      Map.empty,
      force,
      false,
      transitive,
      false
    )

  /** In the form sjson-new decodes to, since `Calendar.equals` also compares the week rules. */
  def genCalendar: Gen[Calendar] =
    Gen
      .long(Range.linear(0L, 4102444800000L))
      .map: millis =>
        GregorianCalendar.from(
          ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
        )

  def genModuleReport(module: ModuleID, evicted: Boolean): Gen[ModuleReport] =
    for
      classifiers <- Gen.list(Gen.element1("", "sources", "javadoc"), Range.linear(0, 2))
      publicationDate <- Gen.choice1(Gen.constant(None), genCalendar.map(Some(_)))
      resolver <- Gen.choice1(Gen.constant(None), genWord.map(Some(_)))
      problem <- Gen.frequency1(5 -> Gen.constant(None), 1 -> genWord.map(Some(_)))
      extra <- Gen.list(genWord.flatMap(k => genWord.map(k -> _)), Range.linear(0, 2))
      licenses <- Gen.list(genWord.map(_ -> None), Range.linear(0, 1))
      callers <- Gen.list(genCaller, Range.linear(0, 3))
    yield
      val artifacts = classifiers.distinct.toVector.map: c =>
        val classifier = Option(c).filter(_.nonEmpty)
        val file = new File(
          artifactBase,
          s"${module.organization}-${module.name}-${module.revision}${classifier.fold("")("-" + _)}.jar"
        )
        Artifact(module.name, "jar", "jar", classifier, Vector.empty, None, Map.empty, None) -> file
      ModuleReport(
        module,
        artifacts,
        Vector.empty,
        None,
        publicationDate,
        resolver,
        resolver,
        evicted,
        None,
        Option.when(evicted)("conflict"),
        problem,
        None,
        extra.toMap,
        Some(true),
        None,
        Vector(ConfigRef("compile")),
        licenses.toVector,
        callers.toVector
      )

  def traverse[A, B](as: List[A])(f: A => Gen[B]): Gen[List[B]] =
    as.foldRight(Gen.constant(List.empty[B])): (a, acc) =>
      for
        b <- f(a)
        bs <- acc
      yield b :: bs

  def shuffled[A](as: Vector[A]): Gen[Vector[A]] =
    Gen
      .list(Gen.int(Range.linear(0, 1000)), Range.singleton(as.size))
      .map(keys => as.zip(keys).sortBy(_._2).map(_._1))

  def genConfigurationReport(name: String): Gen[ConfigurationReport] =
    for
      ids <- Gen.list(genModuleID, Range.linear(0, 8)).map(_.distinct.toVector)
      flags <- Gen.list(
        Gen.frequency1(4 -> Gen.constant(false), 1 -> Gen.constant(true)),
        Range.singleton(ids.size)
      )
      reports <- traverse(ids.zip(flags).toList)((id, evicted) => genModuleReport(id, evicted))
      resolvedOrder <- shuffled(reports.toVector.filterNot(_.evicted))
      keepDetails <- Gen.frequency1(3 -> Gen.constant(true), 1 -> Gen.constant(false))
    yield
      val details =
        if keepDetails then
          reports.toVector
            .groupBy(m => (m.module.organization, m.module.name))
            .toVector
            .sortBy(_._1)
            .map:
              case ((org, n), ms) => OrganizationArtifactReport(org, n, ms)
        else Vector.empty
      ConfigurationReport(ConfigRef(name), resolvedOrder, details)

  def genUpdateReport: Gen[UpdateReport] =
    for
      names <- Gen.list(genConfigName, Range.linear(0, 4)).map(_.distinct)
      configs <- traverse(names)(genConfigurationReport)
      resolveTime <- Gen.long(Range.linear(0L, 100000L))
      downloadTime <- Gen.long(Range.linear(0L, 100000L))
      downloadSize <- Gen.long(Range.linear(0L, 1L << 30))
      stamp <- Gen.choice1(Gen.constant(None), genWord.map(Some(_)))
      stamps <- Gen.list(
        genWord.flatMap(k => Gen.long(Range.linear(0L, Long.MaxValue)).map(k -> _)),
        Range.linear(0, 3)
      )
    yield UpdateReport(
      new File(artifactBase, "ivy.xml"),
      configs.toVector,
      UpdateStats(resolveTime, downloadTime, downloadSize, false, stamp),
      stamps.toMap
    )
end GenUpdateReport
