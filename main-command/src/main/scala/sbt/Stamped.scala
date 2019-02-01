/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.{ File => JFile }
import java.nio.file.Path

import sbt.internal.inc.Stamper
import sbt.io.TypedPath
import xsbti.compile.analysis.Stamp

/**
 * A File that has a compile analysis Stamp value associated with it. In general, the stamp method
 * should be a cached value that can be read without doing any io. This can be used to improve
 * performance anywhere where we need to check if files have changed before doing potentially
 * expensive work.
 */
trait Stamped {
  def stamp: Stamp
}

/**
 * Provides converter functions from TypedPath to [[Stamped]].
 */
object Stamped {
  type File = JFile with Stamped with TypedPath
  def file(typedPath: TypedPath, stamp: Stamp): JFile with Stamped with TypedPath =
    new StampedFileImpl(typedPath, stamp)

  /**
   * Converts a TypedPath instance to a [[Stamped]] by calculating the file hash.
   */
  val sourceConverter: TypedPath => Stamp = tp => Stamper.forHash(tp.toPath.toFile)

  /**
   * Converts a TypedPath instance to a [[Stamped]] using the last modified time.
   */
  val binaryConverter: TypedPath => Stamp = tp => Stamper.forLastModified(tp.toPath.toFile)

  /**
   * A combined convert that converts TypedPath instances representing *.jar and *.class files
   * using the last modified time and all other files using the file hash.
   */
  val converter: TypedPath => Stamp = (tp: TypedPath) =>
    tp.toPath.toString match {
      case s if s.endsWith(".jar")   => binaryConverter(tp)
      case s if s.endsWith(".class") => binaryConverter(tp)
      case _                         => sourceConverter(tp)
  }

  /**
   * Adds a default ordering that just delegates to the java.io.File.compareTo method.
   */
  implicit case object ordering extends Ordering[Stamped.File] {
    override def compare(left: Stamped.File, right: Stamped.File): Int = left.compareTo(right)
  }

  private final class StampedImpl(override val stamp: Stamp) extends Stamped
  private final class StampedFileImpl(typedPath: TypedPath, override val stamp: Stamp)
      extends java.io.File(typedPath.toPath.toString)
      with Stamped
      with TypedPath {
    override def exists: Boolean = typedPath.exists
    override def isDirectory: Boolean = typedPath.isDirectory
    override def isFile: Boolean = typedPath.isFile
    override def isSymbolicLink: Boolean = typedPath.isSymbolicLink
    override def toPath: Path = typedPath.toPath
  }
}
