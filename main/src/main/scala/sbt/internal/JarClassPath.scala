/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File

import sbt.io.IO

private[internal] object JarClassPath {
  class Snapshot private[internal] (val file: File, val lastModified: Long)
      extends Comparable[JarClassPath.Snapshot] {
    private[this] val _hash = (file.hashCode * 31) ^ java.lang.Long.valueOf(lastModified).hashCode()
    def this(file: File) = this(file, IO.getModifiedTimeOrZero(file))
    override def equals(obj: Any): Boolean = obj match {
      case that: JarClassPath.Snapshot =>
        this.lastModified == that.lastModified && this.file == that.file
      case _ => false
    }
    override def hashCode: Int = _hash
    override def compareTo(that: JarClassPath.Snapshot): Int = this.file.compareTo(that.file)
    override def toString: String =
      "Snapshot(path = " + file + ", lastModified = " + lastModified + ")"
  }
}
private[internal] final class JarClassPath(val jars: Seq[File]) {
  private[this] def isSnapshot(file: File): Boolean = file.getName contains "-SNAPSHOT"
  private val jarSet = jars.toSet
  val (snapshotJars, regularJars) = jars.partition(isSnapshot)
  private val snapshots = snapshotJars.map(new JarClassPath.Snapshot(_))

  override def equals(obj: Any): Boolean = obj match {
    case that: JarClassPath => this.jarSet == that.jarSet
    case _                  => false
  }
  // The memoization is because codacy isn't smart enough to identify that
  // `override lazy val hashCode: Int = jarSet.hashCode` does actually override hashCode and it
  // complains that equals and hashCode were not implemented together.
  private[this] lazy val _hashCode: Int = jarSet.hashCode
  override def hashCode: Int = _hashCode
  override def toString: String =
    s"JarClassPath(\n  jars =\n    ${regularJars.mkString(",\n    ")}" +
      s",  snapshots =\n${snapshots.mkString(",\n    ")}\n)"

  /*
   * This is a stricter equality requirement than equals that we can use for cache invalidation.
   */
  private[internal] def strictEquals(that: JarClassPath): Boolean = {
    this.equals(that) && !this.snapshots.view.zip(that.snapshots).exists { case (l, r) => l != r }
  }
}
