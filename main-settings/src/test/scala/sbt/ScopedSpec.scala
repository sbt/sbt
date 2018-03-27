/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import org.scalacheck._, Prop._, util.Pretty

import sbt.internal.util.AttributeKey
import sbt.util.NoJsonWriter
import sbt.{ InputTask, Scope, Task }
import sbt.{ InputKey, Scoped, SettingKey, TaskKey }

import BuildSettingsInstances._

object ScopedSpec extends Properties("Scoped") {
  val intManifest = manifest[Int]
  val stringManifest = manifest[String]

  implicit val arbManifest: Arbitrary[Manifest[_]] =
    Arbitrary(Gen.oneOf(intManifest, stringManifest))

  property("setting keys are structurally equal") = {
    forAll { (label: Label, manifest: Manifest[_], scope: Scope) =>
      val k1 = settingKey(label, manifest, scope)
      val k2 = settingKey(label, manifest, scope)
      expectEq(k1, k2)
    }
  }

  property("task keys are structurally equal") = {
    forAll { (label: Label, manifest: Manifest[_], scope: Scope) =>
      val k1 = taskKey(label, manifest, scope)
      val k2 = taskKey(label, manifest, scope)
      expectEq(k1, k2)
    }
  }

  property("input keys are structurally equal") = {
    forAll { (label: Label, manifest: Manifest[_], scope: Scope) =>
      val k1 = inputKey(label, manifest, scope)
      val k2 = inputKey(label, manifest, scope)
      expectEq(k1, k2)
    }
  }

  property("different key types are not equal") = {
    forAll { (label: Label, manifest: Manifest[_], scope: Scope) =>
      val settingKey1 = settingKey(label, manifest, scope)
      val taskKey1 = taskKey(label, manifest, scope)
      val inputKey1 = inputKey(label, manifest, scope)

      all(
        expectNe(settingKey1, taskKey1),
        expectNe(settingKey1, inputKey1),
        expectNe(taskKey1, inputKey1),
      )
    }
  }

  property("different key types, with the same manifest, are not equal") = {
    forAll { (label: Label, scope: Scope) =>
      val prop1 = {
        val manifest1 = manifest[Task[String]]
        val attrKey = attributeKey(label, manifest1)
        val k1 = SettingKey(attrKey) in scope
        val k2 = TaskKey(attrKey) in scope
        expectNeSameManifest(k1, k2)
      }

      val prop2 = {
        val manifest1 = manifest[InputTask[String]]
        val attrKey = attributeKey(label, manifest1)
        val k1 = SettingKey(attrKey) in scope
        val k2 = InputKey(attrKey) in scope
        expectNeSameManifest(k1, k2)
      }

      all(prop1, prop2)
    }
  }

  ///

  def settingKey[A](label: Label, manifest: Manifest[A], scope: Scope): SettingKey[A] = {
    val noJsonWriter = NoJsonWriter[A]()
    SettingKey[A](label.value)(manifest, noJsonWriter) in scope
  }

  def taskKey[A](label: Label, manifest: Manifest[A], s: Scope): TaskKey[A] =
    TaskKey[A](label.value)(manifest) in s

  def inputKey[A](label: Label, manifest: Manifest[A], scope: Scope): InputKey[A] =
    InputKey[A](label.value)(manifest) in scope

  def attributeKey[A](label: Label, manifest: Manifest[A]): AttributeKey[A] = {
    val jsonWriter = NoJsonWriter[A]()
    AttributeKey[A](label.value)(manifest, jsonWriter)
  }

  ///

  def expectEq(k1: Scoped, k2: Scoped): Prop =
    ?=(k1, k2) && ?=(k2, k1) map eqLabels(k1, k2)

  def expectNe(k1: Scoped, k2: Scoped): Prop =
    !=(k1, k2) && !=(k2, k1) map eqLabels(k1, k2)

  def expectNeSameManifest(k1: Scoped, k2: Scoped) = {
    all(
      ?=(k1.key.manifest, k2.key.manifest), // sanity check the manifests are the same
      expectNe(k1, k2),
    )
  }

  def eqLabels(k1: Scoped, k2: Scoped): Prop.Result => Prop.Result = r => {
    val eqLabel = k1.key.label == k2.key.label
    val eqManifest = k1.key.manifest == k2.key.manifest
    val eqScope = k1.scope == k2.scope
    r.label(s"label equality: ${k1.key.label} == ${k2.key.label} : $eqLabel")
      .label(s"manifest equality: ${k1.key.manifest} == ${k2.key.manifest} : $eqManifest")
      .label(s"scope equality: ${k1.scope} == ${k2.scope} : $eqScope")
  }

  def ?=[T](x: T, y: T)(implicit pp: T => Pretty): Prop =
    if (x == y) proved
    else
      falsified :| {
        val act = Pretty.pretty[T](x, Pretty.Params(0))
        val exp = Pretty.pretty[T](y, Pretty.Params(0))
        s"Expected $act to be equal to $exp"
      }

  def !=[T](x: T, y: T)(implicit pp: T => Pretty): Prop =
    if (x == y) falsified
    else
      proved :| {
        val act = Pretty.pretty[T](x, Pretty.Params(0))
        val exp = Pretty.pretty[T](y, Pretty.Params(0))
        s"Expected $act to NOT be equal to $exp"
      }
}
