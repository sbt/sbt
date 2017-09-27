package sbt

import java.io.File
import sbt.librarymanagement.Configuration

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

  implicit def sbtScopeSyntaxRichReference(r: Reference): RichReference =
    new RichReference(Scope(Select(r), This, This, This))

  implicit def sbtScopeSyntaxRichProject(p: Project): RichReference =
    new RichReference(Scope(Select(p), This, This, This))

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

object SlashSyntax {

  /** RichReference wraps a project to provide the `/` operator for scoping. */
  final class RichReference(s: Scope) {
    def /(c: Configuration): RichConfiguration = new RichConfiguration(s in c)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: SettingKey[A]): SettingKeyThunk[A] = new SettingKeyThunk(s, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: TaskKey[A]): TaskKeyThunk[A] = new TaskKeyThunk(s, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: InputKey[A]): InputKeyThunk[A] = new InputKeyThunk(s, key)
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(s: Scope) {
    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: SettingKey[A]): SettingKeyThunk[A] = new SettingKeyThunk(s, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: TaskKey[A]): TaskKeyThunk[A] = new TaskKeyThunk(s, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: InputKey[A]): InputKeyThunk[A] = new InputKeyThunk(s, key)
  }

  /** RichScope wraps a general scope to provide the `/` operator for scoping. */
  final class RichScope(scope: Scope) {
    def /[A](key: SettingKey[A]): SettingKey[A] = key in scope
    def /[A](key: TaskKey[A]): TaskKey[A] = key in scope
    def /[A](key: InputKey[A]): InputKey[A] = key in scope
  }

  /** RichScopeAxis wraps a project axis to provide the `/` operator to `Zero` for scoping. */
  final class RichScopeAxis(a: ScopeAxis[Reference]) {
    private[this] def toScope: Scope = Scope(a, This, This, This)

    def /(c: Configuration): RichConfiguration = new RichConfiguration(toScope in c)

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
      new RichConfiguration(toScope.copy(config = configAxis))

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: SettingKey[A]): SettingKeyThunk[A] = new SettingKeyThunk(toScope, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: TaskKey[A]): TaskKeyThunk[A] = new TaskKeyThunk(toScope, key)

    // We don't know what the key is for yet, so just capture in a thunk.
    def /[A](key: InputKey[A]): InputKeyThunk[A] = new InputKeyThunk(toScope, key)
  }

  /**
   * SettingKeyThunk is a thunk used to hold a scope and a key
   * while we're not sure if the key is terminal or task-scoping.
   */
  final class SettingKeyThunk[A](base: Scope, key: SettingKey[A]) {
    private[sbt] def materialize: SettingKey[A] = key in base
    private[sbt] def rescope: RichScope = new RichScope(base in key.key)
  }

  /**
   * TaskKeyThunk is a thunk used to hold a scope and a key
   * while we're not sure if the key is terminal or task-scoping.
   */
  final class TaskKeyThunk[A](base: Scope, key: TaskKey[A]) {
    private[sbt] def materialize: TaskKey[A] = key in base
    private[sbt] def rescope: RichScope = new RichScope(base in key.key)
  }

  /**
   * InputKeyThunk is a thunk used to hold a scope and a key
   * while we're not sure if the key is terminal or task-scoping.
   */
  final class InputKeyThunk[A](base: Scope, key: InputKey[A]) {
    private[sbt] def materialize: InputKey[A] = key in base
    private[sbt] def rescope: RichScope = new RichScope(base in key.key)
  }
}
