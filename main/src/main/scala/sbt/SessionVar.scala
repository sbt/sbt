/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import scala.util.control.NonFatal

import sbt.internal.util.{ AttributeMap, IMap, Types }

import Def.ScopedKey
import Types.Id
import Keys.sessionVars
import sjsonnew.JsonFormat
import sbt.ProjectExtra.*

object SessionVar {
  val DefaultDataID = "data"

  // these are required because of inference+manifest limitations
  final case class Key[T](key: ScopedKey[Task[T]])
  final case class Map(map: IMap[Key, Id]) {
    def get[T](k: ScopedKey[Task[T]]): Option[T] = map.get(Key(k))
    def put[T](k: ScopedKey[Task[T]], v: T): Map = Map(map.put(Key(k), v))
  }
  def emptyMap = Map(IMap.empty)

  def persistAndSet[T](key: ScopedKey[Task[T]], state: State, value: T)(using
      f: JsonFormat[T]
  ): State = {
    persist(key, state, value)(using f)
    set(key, state, value)
  }

  def persist[T](key: ScopedKey[Task[T]], state: State, value: T)(using f: JsonFormat[T]): Unit =
    Project.structure(state).streams(state).use(key)(s => s.getOutput(DefaultDataID).write(value))

  def clear(s: State): State = s.put(sessionVars, SessionVar.emptyMap)

  def get[T](key: ScopedKey[Task[T]], state: State): Option[T] =
    orEmpty(state.get(sessionVars)).get(key)

  def set[T](key: ScopedKey[Task[T]], state: State, value: T): State =
    state.update(sessionVars)(om => orEmpty(om).put(key, value))

  def orEmpty(opt: Option[Map]) = opt.getOrElse(emptyMap)

  def transform[S](task: Task[S], f: (State, S) => State): Task[S] =
    val g = (s: S, map: AttributeMap) => map.put(Keys.transformState, (state: State) => f(state, s))
    task.postTransform(g)

  def resolveContext[T](
      key: ScopedKey[Task[T]],
      context: Scope,
      state: State
  ): ScopedKey[Task[T]] =
    val subScoped = Project.replaceThis(context)(key)
    Project
      .structure(state)
      .data
      .definingKey(subScoped)
      .getOrElse(subScoped)

  def read[T](key: ScopedKey[Task[T]], state: State)(using f: JsonFormat[T]): Option[T] =
    Project.structure(state).streams(state).use(key) { s =>
      try {
        Some(s.getInput(key, DefaultDataID).read[T]())
      } catch { case NonFatal(_) => None }
    }

  def load[T](key: ScopedKey[Task[T]], state: State)(using f: JsonFormat[T]): Option[T] =
    get(key, state) orElse read(key, state)(using f)

  def loadAndSet[T](key: ScopedKey[Task[T]], state: State, setIfUnset: Boolean = true)(using
      f: JsonFormat[T]
  ): (State, Option[T]) =
    get(key, state) match {
      case s: Some[T] => (state, s)
      case None =>
        read(key, state)(using f) match {
          case s @ Some(t) =>
            val newState =
              if (setIfUnset && get(key, state).isDefined) state else set(key, state, t)
            (newState, s)
          case None => (state, None)
        }
    }
}
