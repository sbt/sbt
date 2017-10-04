/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import Def.{ displayFull, ScopedKey }
import Keys.taskDefinitionKey

private[sbt] object TaskName {
  def name(node: Task[_]): String = definedName(node) getOrElse anonymousName(node)
  def definedName(node: Task[_]): Option[String] =
    node.info.name orElse transformNode(node).map(displayFull)
  def anonymousName(node: Task[_]): String =
    "<anon-" + System.identityHashCode(node).toHexString + ">"
  def transformNode(node: Task[_]): Option[ScopedKey[_]] =
    node.info.attributes get taskDefinitionKey
}
