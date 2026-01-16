/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package test

import hedgehog.*
import hedgehog.runner.*
import Scope.{ Global, ThisScope }
import ScopeAxis.{ Select, This, Zero }
import SlashSyntax0.*
import BuildSettingsInstances.given
import _root_.sbt.internal.util.AttributeKey

object SlashSyntaxSpec extends Properties:
  override def tests: List[Test] = List(
    property("Global / key", propGlobalKey),
    example("Zero / compile", zeroCompile),
    property("Reference / key", propReferenceKey),
    property("Reference / Config / key", propReferenceConfigKey),
    example("Zero / Zero / compile", zeroZeroCompile),
    property("Reference / task.key / key", propReferenceAttrKeyKey),
    property("Reference / task / key", propReferenceTaskKey),
    property("Reference / inputtask / key", propReferenceInputTaskKey),
    property("Reference / Config / task.key / key", propReferenceConfigAttrKeyKey),
    property("Reference / Config / task / key", propReferenceConfigTaskKey),
    property("Reference / Config / inputtask / key", propReferenceConfigInputTaskKey),
    property("Config / key", propConfigKey),
    property("Config / task.key / key", propConfigAttrKeyKey),
    property("Config / task / key", propConfigTaskKey),
    property("Config / inputtask / key", propConfigInputTaskKey),
    property("task.key / key", propAttrKeyKey),
    property("task / key", propTaskKey),
    property("inputtask / key", propInputTaskKey),
    property("Scope / key", propScopeKey),
    property("Reference / Config? / key", propReferenceConfigAxisKey),
    property("Reference? / key", propReferenceAxisKey),
    property("Reference? / Config? / key", propReferenceAxisConfigAxisKey),
    // property("Reference? / task.key? / key", propReferenceAxisAttrKeyAxisKey),
    property("Reference? / Config? / task.key? / key", propReferenceAxisConfigAxisAttrKeyAxisKey),
  )

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]
  lazy val compile: TaskKey[Unit] = TaskKey[Unit]("compile", "compile")

  def propGlobalKey: Property =
    for
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => Global / k
        case k: TaskKey[?]    => Global / k
        case k: SettingKey[?] => Global / k
    yield Result.assert(
      actual.key == k.key &&
        // Only if the incoming scope is This/This/This,
        // Global scoping is effective.
        (if k.scope == ThisScope then actual.scope == Global
         else true)
    )

  def zeroCompile: Result =
    val actual = Zero / compile
    Result.assert(actual.scope.project == Zero && actual.key == compile.key)

  def zeroZeroCompile: Result =
    val actual = Zero / Zero / compile
    Result.assert(actual.scope.project == Zero && actual.key == compile.key)

  def propReferenceKey: Property =
    for
      ref <- gen[Reference].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / k
        case k: TaskKey[?]    => ref / k
        case k: SettingKey[?] => ref / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true)
    )

  def propReferenceConfigKey: Property =
    for
      ref <- gen[Reference].forAll
      config <- gen[ConfigKey].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / k
        case k: TaskKey[?]    => ref / config / k
        case k: SettingKey[?] => ref / config / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true)
    )

  def propReferenceAttrKeyKey: Property =
    for
      ref <- gen[Reference].forAll
      scoped <- genKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / scoped.key / k
        case k: TaskKey[?]    => ref / scoped.key / k
        case k: SettingKey[?] => ref / scoped.key / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(scoped.key)
         else true)
    )

  def propReferenceTaskKey: Property =
    for
      ref <- gen[Reference].forAll
      t <- genTaskKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / t / k
        case k: TaskKey[?]    => ref / t / k
        case k: SettingKey[?] => ref / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propReferenceInputTaskKey: Property =
    for
      ref <- gen[Reference].forAll
      t <- genInputKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / t / k
        case k: TaskKey[?]    => ref / t / k
        case k: SettingKey[?] => ref / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propReferenceConfigAttrKeyKey: Property =
    for
      ref <- gen[Reference].forAll
      config <- gen[ConfigKey].forAll
      scoped <- genKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / scoped.key / k
        case k: TaskKey[?]    => ref / config / scoped.key / k
        case k: SettingKey[?] => ref / config / scoped.key / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(scoped.key)
         else true)
    )

  def propReferenceConfigTaskKey: Property =
    for
      ref <- gen[Reference].forAll
      config <- gen[ConfigKey].forAll
      t <- genTaskKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / t / k
        case k: TaskKey[?]    => ref / config / t / k
        case k: SettingKey[?] => ref / config / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propReferenceConfigInputTaskKey: Property =
    for
      ref <- gen[Reference].forAll
      config <- gen[ConfigKey].forAll
      t <- genInputKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / t / k
        case k: TaskKey[?]    => ref / config / t / k
        case k: SettingKey[?] => ref / config / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propConfigKey: Property =
    for
      config <- gen[ConfigKey].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => config / k
        case k: TaskKey[?]    => config / k
        case k: SettingKey[?] => config / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true)
    )

  def propConfigAttrKeyKey: Property =
    for
      config <- gen[ConfigKey].forAll
      scoped <- genKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => config / scoped.key / k
        case k: TaskKey[?]    => config / scoped.key / k
        case k: SettingKey[?] => config / scoped.key / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(scoped.key)
         else true)
    )

  def propConfigTaskKey: Property =
    for
      config <- gen[ConfigKey].forAll
      t <- genTaskKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => config / t / k
        case k: TaskKey[?]    => config / t / k
        case k: SettingKey[?] => config / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propConfigInputTaskKey: Property =
    for
      config <- gen[ConfigKey].forAll
      t <- genInputKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => config / t / k
        case k: TaskKey[?]    => config / t / k
        case k: SettingKey[?] => config / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.config == This then actual.scope.config == Select(config)
         else true) &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propAttrKeyKey: Property =
    for
      scoped <- genKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => scoped.key / k
        case k: TaskKey[?]    => scoped.key / k
        case k: SettingKey[?] => scoped.key / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.task == This then actual.scope.task == Select(scoped.key)
         else true)
    )

  def propTaskKey: Property =
    for
      t <- genTaskKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => t / k
        case k: TaskKey[?]    => t / k
        case k: SettingKey[?] => t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propInputTaskKey: Property =
    for
      t <- genInputKey[Unit].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => t / k
        case k: TaskKey[?]    => t / k
        case k: SettingKey[?] => t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.task == This then actual.scope.task == Select(t.key)
         else true)
    )

  def propScopeKey: Property =
    for
      scope <- gen[Scope].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => scope / k
        case k: TaskKey[?]    => scope / k
        case k: SettingKey[?] => scope / k
    yield Result.assert(
      actual.key == k.key &&
        // Only if the incoming scope is This/This/This,
        // Global scoping is effective.
        (if k.scope == ThisScope then actual.scope == scope
         else true)
    )

  def propReferenceConfigAxisKey: Property =
    for
      ref <- gen[Reference].forAll
      config <- gen[ScopeAxis[ConfigKey]].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / k
        case k: TaskKey[?]    => ref / config / k
        case k: SettingKey[?] => ref / config / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == Select(ref)
         else true) &&
        (if k.scope.config == This then actual.scope.config == config
         else true)
    )

  def propReferenceAxisKey: Property =
    for
      ref <- gen[ScopeAxis[Reference]].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / k
        case k: TaskKey[?]    => ref / k
        case k: SettingKey[?] => ref / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == ref
         else true)
    )

  def propReferenceAxisConfigAxisKey: Property =
    for
      ref <- gen[ScopeAxis[Reference]].forAll
      config <- gen[ScopeAxis[ConfigKey]].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / k
        case k: TaskKey[?]    => ref / config / k
        case k: SettingKey[?] => ref / config / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == ref
         else true) &&
        (if k.scope.config == This then actual.scope.config == config
         else true)
    )

  /*
  def propReferenceAxisAttrKeyAxisKey: Property =
    for
      ref <- gen[ScopeAxis[Reference]].forAll
      t <- gen[ScopeAxis[AttributeKey[?]]].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / t / k
        case k: TaskKey[?]    => ref / t / k
        case k: SettingKey[?] => ref / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == ref
         else true) &&
        (if k.scope.task == This then actual.scope.task == t
         else true)
    )
   */

  def propReferenceAxisConfigAxisAttrKeyAxisKey: Property =
    for
      ref <- gen[ScopeAxis[Reference]].forAll
      config <- gen[ScopeAxis[ConfigKey]].forAll
      t <- gen[ScopeAxis[AttributeKey[?]]].forAll
      k <- genKey[Unit].forAll
      actual = k match
        case k: InputKey[?]   => ref / config / t / k
        case k: TaskKey[?]    => ref / config / t / k
        case k: SettingKey[?] => ref / config / t / k
    yield Result.assert(
      actual.key == k.key &&
        (if k.scope.project == This then actual.scope.project == ref
         else true) &&
        (if k.scope.config == This then actual.scope.config == config
         else true) &&
        (if k.scope.task == This then actual.scope.task == t
         else true)
    )
end SlashSyntaxSpec
