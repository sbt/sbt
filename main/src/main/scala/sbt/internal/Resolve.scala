/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.AttributeKey

object Resolve {
  def apply(index: BuildUtil[_],
            current: ScopeAxis[Reference],
            key: AttributeKey[_],
            mask: ScopeMask): Scope => Scope = {
    val rs =
      resolveProject(current, mask) _ ::
        resolveExtra(mask) _ ::
        resolveTask(mask) _ ::
        resolveConfig(index, key, mask) _ ::
        Nil
    scope =>
      (scope /: rs) { (s, f) =>
        f(s)
      }
  }
  def resolveTask(mask: ScopeMask)(scope: Scope): Scope =
    if (mask.task) scope
    else scope.copy(task = Zero)

  def resolveProject(current: ScopeAxis[Reference], mask: ScopeMask)(scope: Scope): Scope =
    if (mask.project) scope
    else scope.copy(project = current)

  def resolveExtra(mask: ScopeMask)(scope: Scope): Scope =
    if (mask.extra) scope
    else scope.copy(extra = Zero)

  def resolveConfig[P](index: BuildUtil[P], key: AttributeKey[_], mask: ScopeMask)(
      scope: Scope): Scope =
    if (mask.config)
      scope
    else {
      val (resolvedRef, proj) = scope.project match {
        case Select(ref) =>
          val r = index resolveRef ref
          (Some(r), index.projectFor(r))
        case Zero | This =>
          (None, index.rootProject(index.root))
      }
      val task = scope.task.toOption
      val keyIndex = index.keyIndex
      val definesKey = (c: ScopeAxis[ConfigKey]) =>
        keyIndex.keys(resolvedRef, c.toOption.map(_.name), task) contains key.label
      val projectConfigs = index.configurations(proj).map(ck => Select(ck))
      val config: ScopeAxis[ConfigKey] = (Zero +: projectConfigs) find definesKey getOrElse Zero
      scope.copy(config = config)
    }
}
