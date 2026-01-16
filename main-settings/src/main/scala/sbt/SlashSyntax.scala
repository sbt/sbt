/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.librarymanagement.Configuration
import sbt.internal.util.AttributeKey
import sbt.ScopeAxis.{ Select, This }
import sbt.Scope.RefThenConfig
import sbt.Scope.RefThenConfig.{ project, config }

/**
 * SlashSyntax implements part of the slash syntax to scope keys for build.sbt DSL.
 *
 * @example
 *  {{{
 *  Test / test := TestResult.Empty
 *  console.key / scalacOptions += "-deprecation"
 *  Compile / console / scalacOptions += "-Ywarn-numeric-widen"
 *  }}}
 */
trait SlashSyntax:
  /**
   * Handles slash syntax for `key.key / key`.
   */
  extension [A1](a: AttributeKey[A1])
    def asScope: Scope = Scope(This, This, Select(a), This)
    def /[K](key: Scoped.ScopingSetting[K]): K = a.asScope.scope(key)

  extension (c: ConfigKey)
    def asScope: Scope = Scope(This, Select(c), This, This)
    def /[K](key: Scoped.ScopingSetting[K]): K = c.asScope.scope(key)
    def /(task: AttributeKey[?]): Scope = c.asScope.copy(task = Select(task))

  extension (c: Configuration)
    def asScope: Scope = (c: ConfigKey).asScope
    def /[K](key: Scoped.ScopingSetting[K]): K = (c: ConfigKey) / key
    def /(task: AttributeKey[?]): Scope = (c: ConfigKey) / task

  extension (in: RefThenConfig)
    def asScope: Scope = in.project.asScope.copy(config = in.config)
    def /[K](key: Scoped.ScopingSetting[K]): K = asScope / key
    def /(task: AttributeKey[?]): Scope = asScope.copy(task = Select(task))

    /** This is for handling `Zero / Zero / Zero / name`. */
    def /(taskAxis: ScopeAxis[AttributeKey[?]]): Scope = asScope.copy(task = taskAxis)
end SlashSyntax

private[sbt] object SlashSyntax0 extends SlashSyntax
