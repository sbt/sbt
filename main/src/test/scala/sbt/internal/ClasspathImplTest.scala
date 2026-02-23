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

object ClasspathImplTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "isAllowedScalaMismatch: 2.13.x and 3.5.x is allowed",
      Result.assert(ClasspathImpl.isAllowedScalaMismatch("2.13.16", "3.5.1"))
    ),
    example(
      "isAllowedScalaMismatch: 3.5.x and 2.13.x is allowed (reverse)",
      Result.assert(ClasspathImpl.isAllowedScalaMismatch("3.5.1", "2.13.16"))
    ),
    example(
      "isAllowedScalaMismatch: 2.13.x and 3.0.0 is allowed",
      Result.assert(ClasspathImpl.isAllowedScalaMismatch("2.13.12", "3.0.0"))
    ),
    example(
      "isAllowedScalaMismatch: 2.13.x and 3.7.0 is allowed",
      Result.assert(ClasspathImpl.isAllowedScalaMismatch("2.13.12", "3.7.0"))
    ),
    example(
      "isAllowedScalaMismatch: 2.13.x and 3.8.0 is NOT allowed",
      Result.assert(!ClasspathImpl.isAllowedScalaMismatch("2.13.12", "3.8.0"))
    ),
    example(
      "isAllowedScalaMismatch: 2.13.x and 3.8.1 is NOT allowed",
      Result.assert(!ClasspathImpl.isAllowedScalaMismatch("2.13.16", "3.8.1"))
    ),
    example(
      "isAllowedScalaMismatch: 2.12.x and 3.5.x is NOT allowed",
      Result.assert(!ClasspathImpl.isAllowedScalaMismatch("2.12.21", "3.5.1"))
    ),
    example(
      "isAllowedScalaMismatch: same versions is NOT a mismatch",
      Result.assert(!ClasspathImpl.isAllowedScalaMismatch("3.5.1", "3.5.1"))
    ),
    example(
      "isAllowedScalaMismatch: 2.12.x and 2.13.x is NOT allowed",
      Result.assert(!ClasspathImpl.isAllowedScalaMismatch("2.12.21", "2.13.16"))
    ),
  )
end ClasspathImplTest
