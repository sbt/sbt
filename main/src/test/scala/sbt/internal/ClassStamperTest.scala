/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.internal.inc.{ Analysis, APIs, Relations }
import _root_.sbt.internal.inc.EmptyStamp
import _root_.sbt.util.{ Digest, Level, Logger }
import xsbti.{ FileConverter, VirtualFile, VirtualFileRef }
import xsbti.api.{ DependencyContext, ExternalDependency, InternalDependency }
import java.nio.file.{ Files, Path }

object ClassStamperTest extends Properties:
  override def tests: List[Test] = List(
    example("returns None for unknown class", returnsNoneForUnknownClass),
    example("returns None for empty analyses", returnsNoneForEmptyAnalyses),
    example("returns Some for class with library dep", returnsSomeForClassWithLibraryDep),
    example("digest reflects internal deps", digestReflectsInternalDeps),
    example("digest reflects library deps", digestReflectsLibraryDeps),
    example("external dep without library is no-op", externalDepWithoutLibraryIsNoOp),
    example("digest is order-independent", digestIsOrderIndependent),
    example("digest is deterministic", digestIsDeterministic),
    example("extraHashes changes the digest", extraHashesChangesDigest),
    example("multiple analyses are walked", multipleAnalysesAreWalked),
    example("cycles do not loop forever", cyclesDoNotLoopForever),
    example("transitive contributions flow up", transitiveContributionsFlowUp),
    example("regression: golden digests", regressionGoldens),
  )

  /**
   * Byte-for-byte regression test of `transitiveStamp`'s output. The library
   * deps' contributions hash file content (= `name`), so values are stable
   * across temp-dir paths.
   */
  def regressionGoldens: Result =
    val internalDepsAnalysis = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )
    val libraryDepsAnalysis = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    val transitiveAnalysis = analysisOf(
      "ABC",
      classes = Seq("A" -> "A", "B" -> "B", "C" -> "C"),
      internalDeps = Seq("A" -> "B", "B" -> "C"),
      externalDeps = Seq("C" -> "ext.C"),
      libraryDeps = Seq(lib("c1") -> "ext.C"),
    )
    val cyclesAnalysis = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B", "B" -> "A"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )

    Result.all(
      List(
        stamp(stamper(internalDepsAnalysis), "A") ====
          Some(
            Digest("sha256-92475004e70f41b94750f4a77bf7b430551113b25d3d57169eadca5692bb043d/64")
          ),
        stamp(stamper(libraryDepsAnalysis), "A") ====
          Some(
            Digest("sha256-c7ade88fc7a21498a6a5e5c385e1f68bed822b72aa63c4a9a48a02c2466ee29e/32")
          ),
        stamp(stamper(transitiveAnalysis), "A") ====
          Some(
            Digest("sha256-7e614445eb7c62ec172e4e899e768794dde97a1ce3c8e3f30e0751948cc9e569/32")
          ),
        stamp(stamper(cyclesAnalysis), "A") ====
          Some(
            Digest("sha256-92475004e70f41b94750f4a77bf7b430551113b25d3d57169eadca5692bb043d/64")
          ),
        stamp(stamper(libraryDepsAnalysis), "A", Seq(Digest.dummy(42L))) ====
          Some(
            Digest("sha256-9d0a61172a43b1e7666f9527f82621395ef6a3a0ce5aed5dac317b2a76e8dd94/48")
          ),
      )
    )

  // ---------- helpers ----------

  private val NoopLogger: Logger = new Logger:
    override def trace(t: => Throwable): Unit = ()
    override def success(message: => String): Unit = ()
    override def log(level: Level.Value, message: => String): Unit = ()

  // Shared temp dir backing all "library JAR" refs. Each `lib(name)` writes
  // a deterministic file once on first access; `StubConverter.toPath` resolves
  // refs into this directory so `Digest.sha256Hash(Path)` can read content.
  private lazy val tmpRoot: Path =
    val d = Files.createTempDirectory("class-stamper-test")
    d.toFile.deleteOnExit()
    d

  private def src(name: String): VirtualFileRef = VirtualFileRef.of(s"$name.scala")

  // Each name maps to a real file with content = name, so stamps are
  // deterministic across runs and distinct between names.
  private def lib(name: String): VirtualFileRef =
    val rel = s"lib/$name.jar"
    val path = tmpRoot.resolve(rel)
    if !Files.exists(path) then
      Files.createDirectories(path.getParent)
      Files.write(path, name.getBytes("UTF-8"))
      path.toFile.deleteOnExit()
    VirtualFileRef.of(rel)

  private val StubConverter: FileConverter = new FileConverter:
    override def toPath(ref: VirtualFileRef): Path = tmpRoot.resolve(ref.id)
    override def toVirtualFile(path: Path): VirtualFile =
      sys.error(s"unexpected toVirtualFile($path)")

  /**
   * Build a one-source `Analysis` where `srcName.scala` defines the given
   * (sourceClassName, binaryClassName) pairs with the given dependencies.
   */
  private def analysisOf(
      srcName: String,
      classes: Iterable[(String, String)] = Nil,
      internalDeps: Iterable[(String, String)] = Nil,
      externalDeps: Iterable[(String, String)] = Nil,
      libraryDeps: Iterable[(VirtualFileRef, String)] = Nil,
  ): Analysis =
    val rels = Relations.empty.addSource(
      src = src(srcName),
      products = Nil,
      classes = classes,
      internalDeps = internalDeps.map { case (a, b) =>
        InternalDependency.of(a, b, DependencyContext.DependencyByMemberRef)
      },
      externalDeps = externalDeps.map { case (a, b) =>
        ExternalDependency.of(
          a,
          b,
          APIs.emptyAnalyzedClass,
          DependencyContext.DependencyByMemberRef,
        )
      },
      libraryDeps = libraryDeps.map { case (vf, cn) => (vf, cn, EmptyStamp) },
    )
    Analysis.empty.copy(relations = rels)

  private def stamper(analyses: Analysis*): ClassStamper =
    new ClassStamper(analyses.toSeq, StubConverter)

  private def stamp(
      s: ClassStamper,
      javaClassName: String,
      extra: Seq[Digest] = Nil,
  ): Option[Digest] =
    s.transitiveStamp(javaClassName, extra, NoopLogger)

  // ---------- tests ----------

  def returnsNoneForUnknownClass: Result =
    val a = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    stamp(stamper(a), "DoesNotExist") ==== None

  def returnsNoneForEmptyAnalyses: Result =
    stamp(stamper(), "Anything") ==== None

  def returnsSomeForClassWithLibraryDep: Result =
    val a = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    Result.assert(stamp(stamper(a), "A").isDefined)

  def digestReflectsInternalDeps: Result =
    val withoutDep = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    val withDep = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )
    val d1 = stamp(stamper(withoutDep), "A")
    val d2 = stamp(stamper(withDep), "A")
    Result.all(
      List(
        Result.assert(d1.isDefined),
        Result.assert(d2.isDefined),
        Result.diffNamed("internal dep should change digest", d1, d2)(_ != _),
      )
    )

  def digestReflectsLibraryDeps: Result =
    val a = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    val b = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("bar") -> "ext.X"),
    )
    val d1 = stamp(stamper(a), "A")
    val d2 = stamp(stamper(b), "A")
    Result.all(
      List(
        Result.assert(d1.isDefined),
        Result.assert(d2.isDefined),
        Result.diffNamed("digests for different libraries should differ", d1, d2)(_ != _),
      )
    )

  def externalDepWithoutLibraryIsNoOp: Result =
    // An external dep with no matching library entry contributes nothing
    // (recursion finds no internal product, no library entry).
    val noExternal = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.Real"),
      libraryDeps = Seq(lib("real") -> "ext.Real"),
    )
    val extraDangling = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.Real", "A" -> "ext.Dangling"),
      libraryDeps = Seq(lib("real") -> "ext.Real"),
    )
    stamp(stamper(noExternal), "A") ==== stamp(stamper(extraDangling), "A")

  def digestIsOrderIndependent: Result =
    val a = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )
    val b = analysisOf(
      "AB",
      classes = Seq("B" -> "B", "A" -> "A"),
      internalDeps = Seq("A" -> "B"),
      externalDeps = Seq("B" -> "ext.Y", "A" -> "ext.X"),
      libraryDeps = Seq(lib("bar") -> "ext.Y", lib("foo") -> "ext.X"),
    )
    stamp(stamper(a), "A") ==== stamp(stamper(b), "A")

  def digestIsDeterministic: Result =
    val a = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )
    // Same stamper instance — exercises the `stamps` cache.
    val s = stamper(a)
    val d1 = stamp(s, "A")
    val d2 = stamp(s, "A")
    // Fresh stamper — bypasses the cache entirely.
    val d3 = stamp(stamper(a), "A")
    Result.all(List(d1 ==== d2, d2 ==== d3))

  def extraHashesChangesDigest: Result =
    val a = analysisOf(
      "A",
      classes = Seq("A" -> "A"),
      externalDeps = Seq("A" -> "ext.X"),
      libraryDeps = Seq(lib("foo") -> "ext.X"),
    )
    val s = stamper(a)
    val d1 = stamp(s, "A", Nil)
    val d2 = stamp(s, "A", Seq(Digest.dummy(42L)))
    Result.all(
      List(
        Result.assert(d1.isDefined),
        Result.assert(d2.isDefined),
        Result.diffNamed("extraHashes should change digest", d1, d2)(_ != _),
      )
    )

  def multipleAnalysesAreWalked: Result =
    // Different classes live in different analyses. transitiveStamp must
    // walk all analyses to find each class.
    val a1 = analysisOf(
      "A1",
      classes = Seq("X" -> "X"),
      externalDeps = Seq("X" -> "ext.Z"),
      libraryDeps = Seq(lib("foo") -> "ext.Z"),
    )
    val a2 = analysisOf(
      "A2",
      classes = Seq("Y" -> "Y"),
      externalDeps = Seq("Y" -> "ext.W"),
      libraryDeps = Seq(lib("bar") -> "ext.W"),
    )
    val both = stamper(a1, a2)
    Result.all(
      List(
        Result.assert(stamp(both, "X").isDefined).log("X (only in a1) should stamp"),
        Result.assert(stamp(both, "Y").isDefined).log("Y (only in a2) should stamp"),
        Result.assert(stamp(stamper(a1), "Y").isEmpty).log("Y must be None without a2"),
        Result.assert(stamp(stamper(a2), "X").isEmpty).log("X must be None without a1"),
      )
    )

  def cyclesDoNotLoopForever: Result =
    // A -> B and B -> A in internal deps. The `alreadySeen` guard prevents
    // infinite recursion.
    val a = analysisOf(
      "AB",
      classes = Seq("A" -> "A", "B" -> "B"),
      internalDeps = Seq("A" -> "B", "B" -> "A"),
      externalDeps = Seq("A" -> "ext.X", "B" -> "ext.Y"),
      libraryDeps = Seq(lib("foo") -> "ext.X", lib("bar") -> "ext.Y"),
    )
    Result.assert(stamp(stamper(a), "A").isDefined)

  def transitiveContributionsFlowUp: Result =
    // A -> B -> C. Changing C's library entry must change A's digest.
    def mk(cLib: VirtualFileRef): Analysis = analysisOf(
      "ABC",
      classes = Seq("A" -> "A", "B" -> "B", "C" -> "C"),
      internalDeps = Seq("A" -> "B", "B" -> "C"),
      externalDeps = Seq("C" -> "ext.C"),
      libraryDeps = Seq(cLib -> "ext.C"),
    )
    val d1 = stamp(stamper(mk(lib("c1"))), "A")
    val d2 = stamp(stamper(mk(lib("c2"))), "A")
    Result.all(
      List(
        Result.assert(d1.isDefined),
        Result.assert(d2.isDefined),
        Result.diffNamed("deep transitive change should reach A", d1, d2)(_ != _),
      )
    )

end ClassStamperTest
