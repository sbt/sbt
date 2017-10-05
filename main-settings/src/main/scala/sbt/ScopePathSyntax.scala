/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import sbt.librarymanagement.Configuration
import sbt.internal.util.AttributeKey
import Def._

/**
 * ScopePathSyntax implements the scope path syntax to scope keys for build.sbt DSL.
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
trait ScopePathSyntax {
  import ScopePathSyntax._

  implicit def sbtSlashSyntaxRichReferenceAxis(a: ScopeAxis[Reference]): RichReference =
    new RichReference(Scope(a, This, This, This))

  implicit def sbtSlashSyntaxRichReference(r: Reference): RichReference = Select(r)
  implicit def sbtSlashSyntaxRichProject[A](p: A)(implicit x: A => Reference): RichReference =
    (p: Reference)

  implicit def sbtScopeSyntaxRichConfiguration(c: Configuration): RichConfiguration =
    new RichConfiguration(Scope(This, Select(c), This, This))

  implicit def sbtScopeSyntaxRichScope(s: Scope): RichScope =
    new RichScope(s)

  implicit def sbtScopeSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(Scope(This, This, Select(t.key), This))

  implicit def sbtScopeSyntaxRichScopeAxis(a: ScopeAxis[Reference]): RichScopeAxis =
    new RichScopeAxis(a)

  // Materialize the setting key thunk
  implicit def sbtScopeSyntaxSettingKeyThunkMaterialize[A](
      thunk: SettingKeyThunk[A]): SettingKey[A] =
    thunk.materialize

  implicit def sbtScopeSyntaxSettingKeyThunkKeyRescope[A](thunk: SettingKeyThunk[A]): RichScope =
    thunk.rescope

  // Materialize the task key thunk
  implicit def sbtScopeSyntaxTaskKeyThunkMaterialize[A](thunk: TaskKeyThunk[A]): TaskKey[A] =
    thunk.materialize

  implicit def sbtScopeSyntaxTaskKeyThunkRescope[A](thunk: TaskKeyThunk[A]): RichScope =
    thunk.rescope

  // Materialize the input key thunk
  implicit def sbtScopeSyntaxInputKeyThunkMaterialize[A](thunk: InputKeyThunk[A]): InputKey[A] =
    thunk.materialize

  implicit def sbtScopeSyntaxInputKeyThunkRescope[A](thunk: InputKeyThunk[A]): RichScope =
    thunk.rescope
}

object ScopePathSyntax {
  sealed trait RichScopeLike {
    protected def toScope: Scope

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: SettingKey[A]): SettingKeyThunk[A] = new SettingKeyThunk(toScope, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: TaskKey[A]): TaskKeyThunk[A] = new TaskKeyThunk(toScope, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: InputKey[A]): InputKeyThunk[A] = new InputKeyThunk(toScope, key)
  }

  /** RichReference wraps a project to provide the `/` operator for scoping. */
  final class RichReference(s: Scope) extends RichScopeLike {
    protected def toScope: Scope = s

    def /(c: Configuration): RichConfiguration = new RichConfiguration(s in c)
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(s: Scope) extends RichScopeLike {
    protected def toScope: Scope = s

    // This is for handling `Zero / Zero / Zero / name`
    def /(taskAxis: ScopeAxis[AttributeKey[_]]): RichScope =
      new RichScope(s.copy(task = taskAxis))
  }

  /** RichScopeAxis wraps a project axis to provide the `/` operator to `Zero` for scoping. */
  final class RichScopeAxis(a: ScopeAxis[Reference]) extends RichScopeLike {
    protected def toScope: Scope = Scope(a, This, This, This)

    def /(c: Configuration): RichConfiguration = new RichConfiguration(toScope in c)

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
      new RichConfiguration(toScope.copy(config = configAxis))
  }
}
