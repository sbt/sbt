/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File

import sbt.Def.{ Initialize, ScopedKey, Setting }
import sbt.Keys._
import sbt.internal.{ LibraryManagement, OverrideConfigs }
import sbt.internal.util._
import sbt.internal.util.complete._
import sbt.io._
import sbt.io.syntax._
import sbt.librarymanagement._
import sjsonnew._

trait BuildCommon {
  def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = LibraryManagement.lock(app)

  /**
   * Allows a String to be used where a `NameFilter` is expected.
   * Asterisks (`*`) in the string are interpreted as wildcards.
   * All other characters must match exactly.  See GlobFilter.
   */
  implicit def globFilter(expression: String): NameFilter = GlobFilter(expression)

  implicit def richAttributed(s: Seq[Attributed[File]]): RichAttributed = new RichAttributed(s)
  implicit def richFiles(s: Seq[File]): RichFiles = new RichFiles(s)
  implicit def richPathFinder(s: PathFinder): RichPathFinder = new RichPathFinder(s)
  final class RichPathFinder private[sbt] (s: PathFinder) {

    /** Converts the `PathFinder` to a `Classpath`, which is an alias for `Seq[Attributed[File]]`. */
    def classpath: Classpath = Attributed blankSeq s.get
  }
  final class RichAttributed private[sbt] (s: Seq[Attributed[File]]) {

    /** Extracts the plain `Seq[File]` from a Classpath (which is a `Seq[Attributed[File]]`).*/
    def files: Seq[File] = Attributed.data(s)
  }
  final class RichFiles private[sbt] (s: Seq[File]) {

    /** Converts the `Seq[File]` to a Classpath, which is an alias for `Seq[Attributed[File]]`. */
    def classpath: Classpath = Attributed blankSeq s
  }

  def overrideConfigs(cs: Configuration*)(
      configurations: Seq[Configuration]
  ): Seq[Configuration] = OverrideConfigs(cs: _*)(configurations)

  // these are intended for use in in put tasks for creating parsers
  def getFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State): Option[T] =
    SessionVar.get(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  def loadFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State)(
      implicit f: JsonFormat[T]
  ): Option[T] =
    SessionVar.load(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

  // intended for use in constructing InputTasks
  def loadForParser[P, T](task: TaskKey[T])(
      f: (State, Option[T]) => Parser[P]
  )(implicit format: JsonFormat[T]): Initialize[State => Parser[P]] =
    loadForParserI(task)(Def value f)(format)
  def loadForParserI[P, T](task: TaskKey[T])(
      init: Initialize[(State, Option[T]) => Parser[P]]
  )(implicit format: JsonFormat[T]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, loadFromContext(task, resolvedScoped.value, s)(format))
    }

  def getForParser[P, T](
      task: TaskKey[T]
  )(init: (State, Option[T]) => Parser[P]): Initialize[State => Parser[P]] =
    getForParserI(task)(Def value init)
  def getForParserI[P, T](
      task: TaskKey[T]
  )(init: Initialize[(State, Option[T]) => Parser[P]]): Initialize[State => Parser[P]] =
    Def.setting { (s: State) =>
      init.value(s, getFromContext(task, resolvedScoped.value, s))
    }

  // these are for use for constructing Tasks
  def loadPrevious[T](task: TaskKey[T])(implicit f: JsonFormat[T]): Initialize[Task[Option[T]]] =
    Def.task { loadFromContext(task, resolvedScoped.value, state.value)(f) }
  def getPrevious[T](task: TaskKey[T]): Initialize[Task[Option[T]]] =
    Def.task { getFromContext(task, resolvedScoped.value, state.value) }

  private[sbt] def derive[T](s: Setting[T]): Setting[T] =
    Def.derive(s, allowDynamic = true, trigger = _ != streams.key, default = true)
}
