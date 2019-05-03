/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.IOException
import java.nio.file.Path

import sbt.internal.nio.{ FileEvent, FileTreeRepository, Observable, Observer }
import sbt.nio.file.Glob

private[sbt] object FileManagement {
  private[sbt] def copy[T](fileTreeRepository: FileTreeRepository[T]): FileTreeRepository[T] =
    new CopiedFileTreeRepository[T](fileTreeRepository)
  private[this] class CopiedFileTreeRepository[T](underlying: FileTreeRepository[T])
      extends FileTreeRepository[T] {
    override def list(path: Path): Seq[(Path, T)] = underlying.list(path)
    override def close(): Unit = {}
    override def register(glob: Glob): Either[IOException, Observable[FileEvent[T]]] =
      underlying.register(glob)
    override def addObserver(observer: Observer[FileEvent[T]]): AutoCloseable =
      underlying.addObserver(observer)
    override def toString: String = s"CopiedFileTreeRepository($underlying)"
  }
}
