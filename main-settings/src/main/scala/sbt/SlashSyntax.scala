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
trait SlashSyntax:
  import SlashSyntax.*

  /*
  given Conversion[ScopeAxis[Reference], RichReference] =
    (a: ScopeAxis[Reference]) => RichReference(Scope(a, This, This, This))
   */
  implicit def sbtSlashSyntaxRichReferenceAxis(a: ScopeAxis[Reference]): RichReference =
    new RichReference(Scope(a, This, This, This))

  /*
  given [A](using Conversion[A, Reference]): Conversion[A, RichReference] =
    (a: A) => Select(a: Reference)
   */
  implicit def sbtSlashSyntaxRichProject[A](p: A)(implicit x: A => Reference): RichReference = x(p)

  /*
  given Conversion[Reference, RichReference] =
    (r: Reference) => Select(r)
   */
  implicit def sbtSlashSyntaxRichReference(r: Reference): RichReference = Select(r)

  /*
  given Conversion[ConfigKey, RichConfiguration] =
    (c: ConfigKey) => RichConfiguration(Scope(This, Select(c), This, This))
   */
  implicit def sbtSlashSyntaxRichConfigKey(c: ConfigKey): RichConfiguration =
    new RichConfiguration(Scope(This, Select(c), This, This))

  /*
  given Conversion[Configuration, RichConfiguration] =
    (c: Configuration) => (c: ConfigKey)
   */
  implicit def sbtSlashSyntaxRichConfiguration(c: Configuration): RichConfiguration = (c: ConfigKey)

  /**
   * This handles task scoping an existing scoped key (such as `Compile / test`)
   * into a task scoping in `(Compile / test) / name`.
   */
  /*
  given Conversion[Scoped, Scope] =
    (t: Scoped) => t.scope.copy(task = Select(t.key))

  given Conversion[Scoped, RichScope] =
    (t: Scoped) => RichScope(t: Scope)
   */
  implicit def sbtSlashSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(t.scope.copy(task = Select(t.key)))
  /*
  given [A1]: Conversion[AttributeKey[A1], Scope] =
    (a: AttributeKey[A1]) => Scope(This, This, Select(a), This)

  given [A1]: Conversion[AttributeKey[A1], RichScope] =
    (a: AttributeKey[A1]) => RichScope(a: Scope)
   */
  implicit def sbtSlashSyntaxRichScopeFromAttributeKey(a: AttributeKey[_]): RichScope =
    Scope(This, This, Select(a), This)

  /*
  given Conversion[Scope, RichScope] =
    (scope: Scope) => RichScope(scope)
   */
  implicit def sbtSlashSyntaxRichScope(s: Scope): RichScope = new RichScope(s)

end SlashSyntax

object SlashSyntax:

  sealed trait HasSlashKey {
    protected def scope: Scope
    def /[K](key: Scoped.ScopingSetting[K]): K = key.rescope(scope)
  }

  sealed trait HasSlashKeyOrAttrKey extends HasSlashKey {
    def /(key: AttributeKey[_]): Scope = scope.rescope(key)
  }

  /** RichReference wraps a reference to provide the `/` operator for scoping. */
  final class RichReference(protected val scope: Scope) extends HasSlashKeyOrAttrKey {
    def /(c: ConfigKey): RichConfiguration = new RichConfiguration(scope.rescope(c))

    def /(c: Configuration): RichConfiguration = new RichConfiguration(scope.rescope(c))

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
      new RichConfiguration(scope.copy(config = configAxis))
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(protected val scope: Scope) extends HasSlashKeyOrAttrKey {
    // This is for handling `Zero / Zero / Zero / name`.
    def /(taskAxis: ScopeAxis[AttributeKey[_]]): Scope =
      scope.copy(task = taskAxis)
  }

  /**
   * RichScope wraps a general scope to provide the `/` operator for scoping.
   */
  final class RichScope(protected val scope: Scope) extends HasSlashKeyOrAttrKey

end SlashSyntax

private[sbt] object SlashSyntax0 extends SlashSyntax
