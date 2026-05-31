/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 *
 */

package sbt.internal.util.hashing

import java.io.File
import java.nio.file.Path as NioPath

trait FileHash:
  def hash(file: File): Long
  def hash(file: NioPath): Long
  override def toString(): String =
    getClass().getSimpleName()
end FileHash
