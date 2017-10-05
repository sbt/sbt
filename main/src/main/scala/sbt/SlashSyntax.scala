/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import sbt.librarymanagement.Configuration
import sbt.internal.util.AttributeKey

/**
 * SlashSyntax implements the slash syntax to scope keys for build.sbt DSL.
 * The implicits are set up such that the order that the scope components
 * must appear in the order of the project axis, the configuration axis, and
 * the task axis. This ordering is the same as the shell syntax.
 *
 * @example
 *  {{{
 *  Global / cancelable := true
 *  ThisBuild / scalaVersion := "2.12.2"
 *  Test / test := ()
 *  console / scalacOptions += "-deprecation"
 *  Compile / console / scalacOptions += "-Ywarn-numeric-widen"
 *  projA / Compile / console / scalacOptions += "-feature"
 *  Zero / Zero / name := "foo"
 *  }}}
 */
trait SlashSyntax {
  import SlashSyntax._

  implicit def sbtScopePathSyntaxRichReferenceAxis(a: ScopeAxis[Reference]): RichReference =
    new RichReference(Scope(a, This, This, This))

  implicit def sbtScopePathSyntaxRichReference(r: Reference): RichReference = Select(r)
  implicit def sbtScopePathSyntaxRichProject[A](p: A)(implicit x: A => Reference): RichReference =
    (p: Reference)

  implicit def sbtScopePathSyntaxRichConfiguration(c: Configuration): RichConfiguration =
    new RichConfiguration(Scope(This, Select(c), This, This))

  implicit def sbtScopePathSyntaxRichScope(s: Scope): RichScope =
    new RichScope(s)

  /**
   * This handles task scoping an existing scoped key (such as `Compile / test`)
   * into a task scoping in `(Compile / test) / name`.
   */
  implicit def sbtScopePathSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(t.scope.copy(task = Select(t.key)))
}

object SlashSyntax {

  /** RichScopeLike wraps a general scope to provide the `/` operator for key scoping. */
  sealed trait RichScopeLike {
    protected def scope: Scope
    def /[A](key: SettingKey[A]): SettingKey[A] = key in scope
    def /[A](key: TaskKey[A]): TaskKey[A] = key in scope
    def /[A](key: InputKey[A]): InputKey[A] = key in scope
  }

  /** RichReference wraps a reference to provide the `/` operator for scoping. */
  final class RichReference(protected val scope: Scope) extends RichScopeLike {
    def /(c: Configuration): RichConfiguration = new RichConfiguration(scope in c)

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
      new RichConfiguration(scope.copy(config = configAxis))
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(protected val scope: Scope) extends RichScopeLike {
    // This is for handling `Zero / Zero / Zero / name`.
    def /(taskAxis: ScopeAxis[AttributeKey[_]]): RichScope =
      new RichScope(scope.copy(task = taskAxis))
  }

  /** RichScope wraps a general scope to provide the `/` operator for scoping. */
  final class RichScope(protected val scope: Scope) extends RichScopeLike {}
}
