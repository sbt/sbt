/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

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

  implicit def sbtSlashSyntaxRichReferenceAxis(a: ScopeAxis[Reference]): RichReference =
    new RichReference(Scope(a, This, This, This))

  implicit def sbtSlashSyntaxRichReference(r: Reference): RichReference = Select(r)
  implicit def sbtSlashSyntaxRichProject[A](p: A)(implicit x: A => Reference): RichReference =
    (p: Reference)

  implicit def sbtSlashSyntaxRichConfigKey(c: ConfigKey): RichConfiguration =
    new RichConfiguration(Scope(This, Select(c), This, This))

  implicit def sbtSlashSyntaxRichConfiguration(c: Configuration): RichConfiguration = (c: ConfigKey)

  implicit def sbtSlashSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(Scope(This, This, Select(t.key), This))

  implicit def sbtSlashSyntaxRichScope(s: Scope): RichScope = new RichScope(s)

  implicit def sbtSlashSyntaxScopeAndKeyRescope(scopeAndKey: ScopeAndKey[_]): TerminalScope =
    scopeAndKey.rescope

  implicit def sbtSlashSyntaxScopeAndKeyMaterialize[K <: Key[K]](scopeAndKey: ScopeAndKey[K]): K =
    scopeAndKey.materialize
}

object SlashSyntax {

  /** RichReference wraps a reference to provide the `/` operator for scoping. */
  final class RichReference(protected val scope: Scope) extends RichScopeLike {
    def /(c: ConfigKey): RichConfiguration = new RichConfiguration(scope in c)
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

  sealed trait RichScopeLike {
    protected def scope: Scope

    // We don't know what the key is for yet, so just capture for now.
    def /[A](key: SettingKey[A]): ScopeAndSettingKey[A] = new ScopeAndSettingKey(scope, key)
    def /[A](key: TaskKey[A]): ScopeAndTaskKey[A] = new ScopeAndTaskKey(scope, key)
    def /[A](key: InputKey[A]): ScopeAndInputKey[A] = new ScopeAndInputKey(scope, key)
  }

  /** RichScope wraps a general scope to provide the `/` operator for scoping. */
  final class RichScope(protected val scope: Scope) extends RichScopeLike

  /** TerminalScope provides the last `/` for scoping. */
  final class TerminalScope(scope: Scope) {
    def /[K](key: Scoped.ScopingSetting[K]): K = key in scope
  }

  /** Both `Scoped.ScopingSetting` and `Scoped` are parents of `SettingKey`, `TaskKey` and
   * `InputKey`. We'll need both, so this is a convenient type alias. */
  type Key[K] = Scoped.ScopingSetting[K] with Scoped

  /**
   * ScopeAndKey is a synthetic DSL construct necessary to capture both the built-up scope with a
   * given key, while we're not sure if the given key is terminal or task-scoping. The "materialize"
   * method will be used if it's terminal, returning the scoped key, while "rescope" will be used
   * if we're task-scoping.
   *
   * @tparam K the type of the given key, necessary to type "materialize"
   */
  sealed trait ScopeAndKey[K <: Key[K]] {
    protected[this] def scope: Scope
    protected[this] def key: K

    final private[sbt] def materialize: K = key in scope
    final private[sbt] def rescope: TerminalScope = new TerminalScope(scope in key.key)

    final override def toString: String = s"$scope / ${key.key}"
  }

  final class ScopeAndSettingKey[A](
      protected[this] val scope: Scope,
      protected[this] val key: SettingKey[A]
  ) extends ScopeAndKey[SettingKey[A]]
      with Def.KeyedInitialize[A] {
    def scopedKey = materialize.scopedKey
  }

  final class ScopeAndTaskKey[A](
      protected[this] val scope: Scope,
      protected[this] val key: TaskKey[A]
  ) extends ScopeAndKey[TaskKey[A]]
      with Def.KeyedInitialize[Task[A]] {
    def scopedKey = materialize.scopedKey
  }

  final class ScopeAndInputKey[A](
      protected[this] val scope: Scope,
      protected[this] val key: InputKey[A]
  ) extends ScopeAndKey[InputKey[A]]
      with Def.KeyedInitialize[InputTask[A]] {
    def scopedKey = materialize.scopedKey
  }

}
