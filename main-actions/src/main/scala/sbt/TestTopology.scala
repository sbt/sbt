/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

/**
 * Each subproject declares its testTopology setting to inform how
 * its test classes are grouped together for scheduling.
 */
enum TestTopology:
  case Default
  case ClassParallel
  case SubprojectExclusive
  case SubprojectSplit(n: Int)

object TestTopology:
  private final val defaultSplit = 2
  def default: TestTopology = TestTopology.Default
  def subprojectExclusive: TestTopology = TestTopology.SubprojectExclusive
  def subprojectSplit(n: Int): TestTopology = TestTopology.SubprojectSplit(n)

  private[sbt] def isSingleGroup(topo: TestTopology, fork: Boolean): Boolean =
    !fork || (topo match
      case TestTopology.SubprojectExclusive => true
      case _                                => false)

  private[sbt] def requestedSplit(topo: TestTopology): Int =
    topo match
      case TestTopology.SubprojectExclusive => 1
      case TestTopology.SubprojectSplit(n)  => n
      case _                                => defaultSplit
end TestTopology
