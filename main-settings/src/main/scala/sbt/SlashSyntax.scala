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

  implicit def sbtSlashSyntaxRichScope(s: Scope): TerminalScope = new TerminalScope(s)

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

  /** Both `Scoped.ScopingSetting` and `Scoped` are parents of `SettingKey`, `TaskKey` and
   * `InputKey`. We'll need both, so this is a convenient type alias. */
  type Key[K] = Scoped.ScopingSetting[K] with Scoped

  sealed trait RichScopeLike {
    protected def scope: Scope

    // We don't know what the key is for yet, so just capture for now.
    def /[K <: Key[K]](key: K): ScopeAndKey[K] = new ScopeAndKey(scope, key)
  }

  /** RichScope wraps a general scope to provide the `/` operator for scoping. */
  final class RichScope(protected val scope: Scope) extends RichScopeLike

  /** TerminalScope provides the last `/` for scoping. */
  final class TerminalScope(scope: Scope) {
    def /[K <: Key[K]](key: K): K = key in scope
  }

  /**
   * ScopeAndKey is a synthetic DSL construct necessary to capture both the built-up scope with a
   * given key, while we're not sure if the given key is terminal or task-scoping. The "materialize"
   * method will be used if it's terminal, returning the scoped key, while "rescope" will be used
   * if we're task-scoping.
   *
   * @param scope the built-up scope
   * @param key a given key
   * @tparam K the type of the given key, necessary to type "materialize"
   */
  final class ScopeAndKey[K <: Key[K]](scope: Scope, key: K) {
    private[sbt] def materialize: K = key in scope
    private[sbt] def rescope: TerminalScope = new TerminalScope(scope in key.key)

    override def toString: String = s"$scope / ${key.key}"
  }

}
