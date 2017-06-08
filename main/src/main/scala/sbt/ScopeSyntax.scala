package sbt

import java.io.File
import Scoped.{ RichFileSetting, RichFilesSetting }

/**
 * ScopeSyntax implements the slash syntax to scope keys for build.sbt DSL.
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
trait ScopeSyntax {
  import ScopeSyntax._

  implicit def sbtScopeSyntaxRichReference(r: Reference): RichReference =
    new RichReference(Scope(Select(r), This, This, This))

  implicit def sbtScopeSyntaxRichProject(p: Project): RichReference =
    new RichReference(Scope(Select(p), This, This, This))

  implicit def sbtScopeSyntaxRichConfiguration(c: Configuration): RichConfiguration =
    new RichConfiguration(Scope(This, Select(c), This, This))

  implicit def sbtScopeSyntaxRichScope(s: Scope): RichScope =
    new RichScope(s)

  implicit def sbtScopeSyntaxRichScopeFromScoped(t: Scoped): RichScope =
    new RichScope(t.scope.copy(task = Select(t.key)))

  implicit def sbtScopeSyntaxRichScopeAxis(a: ScopeAxis[Reference]): RichScopeAxis =
    new RichScopeAxis(a)

  // This is for sbt 0.13 compat. Remove for sbt 1.0.
  implicit def sbtScopeSyntaxRichFileSetting(s: SettingKey[File]): RichFileSetting =
    new RichFileSetting(s)

  // This is for sbt 0.13 compat. Remove for sbt 1.0.
  implicit def sbtScopeSyntaxRichFilesSetting(s: SettingKey[Seq[File]]): RichFilesSetting =
    new RichFilesSetting(s)
}

object ScopeSyntax {
  /** RichReference wraps a project to provide the `/` operator for scoping. */
  final class RichReference(scope: Scope) {
    def /(c: ConfigKey): RichConfiguration = new RichConfiguration(scope in c)
    def /[A](key: SettingKey[A]): SettingKey[A] = key in scope
    def /[A](key: TaskKey[A]): TaskKey[A] = key in scope
    def /[A](key: InputKey[A]): InputKey[A] = key in scope
  }

  /** RichConfiguration wraps a configuration to provide the `/` operator for scoping. */
  final class RichConfiguration(scope: Scope) {
    def /[A](key: SettingKey[A]): SettingKey[A] = key in scope
    def /[A](key: TaskKey[A]): TaskKey[A] = key in scope
    def /[A](key: InputKey[A]): InputKey[A] = key in scope
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

    def /(c: ConfigKey): RichConfiguration = new RichConfiguration(toScope in c)

    // This is for handling `Zero / Zero / name`.
    def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration = new RichConfiguration(toScope.copy(config = configAxis))

    def /[A](key: SettingKey[A]): SettingKey[A] = key in toScope
    def /[A](key: TaskKey[A]): TaskKey[A] = key in toScope
    def /[A](key: InputKey[A]): InputKey[A] = key in toScope
  }
}
