/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def.{ displayFull, ScopedKey }
import Keys.taskDefinitionKey

private[sbt] object TaskName {
  def name(node: Task[?]): String = definedName(node).getOrElse(anonymousName(node))
  def definedName(node: Task[?]): Option[String] =
    node.name.orElse(transformNode(node).map(displayFull))
  def anonymousName(node: TaskId[?]): String =
    "<anon-" + System.identityHashCode(node).toHexString + ">"
  def transformNode(node: Task[?]): Option[ScopedKey[?]] =
    node.get(taskDefinitionKey)
}
