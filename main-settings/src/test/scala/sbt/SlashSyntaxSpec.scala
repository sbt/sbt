/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.test

import org.scalacheck.{ Test => _, _ }, Prop._

import sbt.SlashSyntax
import sbt.{ Scope, ScopeAxis, Scoped }, Scope.{ Global, ThisScope }
import sbt.Reference
import sbt.ConfigKey
import sbt.internal.util.AttributeKey

import BuildSettingsInstances._

object SlashSyntaxSpec extends Properties("SlashSyntax") with SlashSyntax {
  property("Global / key == key in Global") = {
    forAll((k: Key) => expectValue(k in Global)(Global / k))
  }

  property("Reference / key == key in Reference") = {
    forAll((r: Reference, k: Key) => expectValue(k in r)(r / k))
  }

  property("Reference / Config / key == key in Reference in Config") = {
    forAll((r: Reference, c: ConfigKey, k: Key) => expectValue(k in r in c)(r / c / k))
  }

  property("Reference / task.key / key == key in Reference in task") = {
    forAll((r: Reference, t: Scoped, k: Key) => expectValue(k in (r, t))(r / t.key / k))
  }

  property("Reference / task / key ~= key in Reference in task") = {
    import WithoutScope._
    forAll((r: Reference, t: Key, k: Key) => expectValue(k in (r, t))(r / t / k))
  }

  property("Reference / Config / task.key / key == key in Reference in Config in task") = {
    forAll { (r: Reference, c: ConfigKey, t: Scoped, k: Key) =>
      expectValue(k in (r, c, t))(r / c / t.key / k)
    }
  }

  property("Reference / Config / task / key ~= key in Reference in Config in task") = {
    import WithoutScope._
    forAll { (r: Reference, c: ConfigKey, t: Key, k: Key) =>
      expectValue(k in (r, c, t))(r / c / t / k)
    }
  }

  property("Config / key == key in Config") = {
    forAll((c: ConfigKey, k: Key) => expectValue(k in c)(c / k))
  }

  property("Config / task.key / key == key in Config in task") = {
    forAll((c: ConfigKey, t: Scoped, k: Key) => expectValue(k in c in t)(c / t.key / k))
  }

  property("Config / task / key ~= key in Config in task") = {
    import WithoutScope._
    forAll((c: ConfigKey, t: Key, k: Key) => expectValue(k in c in t)(c / t / k))
  }

  property("task.key / key == key in task") = {
    forAll((t: Scoped, k: Key) => expectValue(k in t)(t.key / k))
  }

  property("task / key ~= key in task") = {
    import WithoutScope._
    forAll((t: Key, k: Key) => expectValue(k in t)(t / k))
  }

  property("Scope / key == key in Scope") = {
    forAll((s: Scope, k: Key) => expectValue(k in s)(s / k))
  }

  property("Reference? / key == key in ThisScope.copy(..)") = {
    forAll { (r: ScopeAxis[Reference], k: Key) =>
      expectValue(k in ThisScope.copy(project = r))(r / k)
    }
  }

  property("Reference? / ConfigKey? / key == key in ThisScope.copy(..)") = {
    forAll(
      (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], k: Key) =>
        expectValue(k in ThisScope.copy(project = r, config = c))(r / c / k)
    )
  }

//  property("Reference? / AttributeKey? / key == key in ThisScope.copy(..)") = {
//    forAll((r: ScopeAxis[Reference], t: ScopeAxis[AttributeKey[_]], k: AnyKey) =>
//      expectValue(k in ThisScope.copy(project = r, task = t))(r / t / k))
//  }

  property("Reference? / ConfigKey? / AttributeKey? / key == key in ThisScope.copy(..)") = {
    forAll {
      (r: ScopeAxis[Reference], c: ScopeAxis[ConfigKey], t: ScopeAxis[AttributeKey[_]], k: Key) =>
        expectValue(k in ThisScope.copy(project = r, config = c, task = t))(r / c / t / k)
    }
  }

  def expectValue(expected: Scoped)(x: Scoped) = {
    val equals = x.scope == expected.scope && x.key == expected.key
    if (equals) proved else falsified :| s"Expected $expected but got $x"
  }
}
