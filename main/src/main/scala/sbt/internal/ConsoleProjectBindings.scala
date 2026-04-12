/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import scala.compiletime.uninitialized

/**
 * Static holder for consoleProject bindings.
 *
 * Scala 3 compiler bridge does not implement REPL binding injection
 * (https://github.com/scala/scala3/issues/5069), so we store the runtime
 * objects here and generate `val` definitions in `initialCommands` that
 * read from this holder.
 *
 * Must NOT be `private[sbt]` because the Scala 3 REPL compiles
 * `initialCommands` outside the `sbt` package.
 */
object ConsoleProjectBindings:
  @volatile private var _state: State = uninitialized
  @volatile private var _extracted: Extracted = uninitialized
  @volatile private var _cpHelpers: ConsoleProject.Imports = uninitialized

  def set(state: State, extracted: Extracted, cpHelpers: ConsoleProject.Imports): Unit =
    _state = state
    _extracted = extracted
    _cpHelpers = cpHelpers

  def clear(): Unit =
    _state = null
    _extracted = null
    _cpHelpers = null

  def state: State = _state
  def extracted: Extracted = _extracted
  def cpHelpers: ConsoleProject.Imports = _cpHelpers
end ConsoleProjectBindings
