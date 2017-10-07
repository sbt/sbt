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

  implicit def sbtSlashSyntaxRichScope(s: Scope): RichScope = new RichScope(s)

  /**
   * This handles task scoping an existing scoped key (such as `Compile / test`)
   * into a task scoping in `(Compile / test) / name`.
   */
  implicit def sbtSlashSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(t.scope.copy(task = Select(t.key)))

  implicit def sbtSlashSyntaxRichScopeFromAttributeKey(a: AttributeKey[_]): RichScope =
    Scope(This, This, Select(a), This)

}

object SlashSyntax {

  sealed trait HasSlashKey {
    protected def scope: Scope
    final def /[K](key: Scoped.ScopingSetting[K]): K = key in scope
  }

  sealed trait HasSlashKeyOrAttrKey extends HasSlashKey {
    final def /(key: AttributeKey[_]): RichScope = new RichScope(scope in key)
  }

  /** RichReference wraps a reference to provide the `/` operator for scoping. */
  final class RichReference(protected val scope: Scope) extends HasSlashKeyOrAttrKey {
    def /(c: ConfigKey): RichConfiguration = new RichConfiguration(scope in c)
    def /(c: Configuration): RichConfiguration = new RichConfiguration(scope in c)

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
      new RichConfiguration(scope.copy(config = configAxis))
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(protected val scope: Scope) extends HasSlashKeyOrAttrKey {
    // This is for handling `Zero / Zero / Zero / name`.
    def /(taskAxis: ScopeAxis[AttributeKey[_]]): RichScope =
      new RichScope(scope.copy(task = taskAxis))
  }

  /** RichScope wraps a general scope to provide the `/` operator for scoping. */
  final class RichScope(protected val scope: Scope) extends HasSlashKey

}
