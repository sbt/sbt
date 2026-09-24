/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.util.CacheImplicits.given
import _root_.sbt.util.DigestHasher
import xsbti.{ HashedVirtualFileRef, VirtualFileRef }

/**
 * The compile action cache is keyed on [[CompileInputs2]], so every setting that changes the
 * compile outcome has to be part of it (#9748).
 */
object CompileInputs2HashTest extends Properties:
  override def tests: List[Test] = List(
    example("compileOrder changes the hash", compileOrderChangesHash),
    example("pipelining changes the hash", pipeliningChangesHash),
    example("identical inputs hash the same", identicalInputsHashTheSame),
  )

  private def base = CompileInputs2(
    classpath = Vector(HashedVirtualFileRef.of("${BASE}/lib/dep.jar", "sha256-abc", 1L)),
    sources = Vector(HashedVirtualFileRef.of("${BASE}/src/main/scala/A.scala", "sha256-def", 2L)),
    scalacOptions = Vector("-deprecation"),
    javacOptions = Vector.empty,
    outputPath = VirtualFileRef.of("${BASE}/target/classes"),
    cachePath = VirtualFileRef.of("${BASE}/target/inc_compile.zip"),
    incrementalOptions = Vector.empty,
    scalaVersion = "2.13.18",
    compileOrder = "Mixed",
    pipelining = false,
  )

  private def compileOrderChangesHash: Result =
    val mixed = DigestHasher.hashUnsafe(base)
    val javaThenScala = DigestHasher.hashUnsafe(base.copy(compileOrder = "JavaThenScala"))
    Result.assert(mixed != javaThenScala)

  private def pipeliningChangesHash: Result =
    val off = DigestHasher.hashUnsafe(base)
    val on = DigestHasher.hashUnsafe(base.copy(pipelining = true))
    Result.assert(off != on)

  private def identicalInputsHashTheSame: Result =
    DigestHasher.hashUnsafe(base) ==== DigestHasher.hashUnsafe(base)
end CompileInputs2HashTest
