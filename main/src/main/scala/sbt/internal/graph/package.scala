/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

package object graph {
  type Edge = (GraphModuleId, GraphModuleId)
  def Edge(from: GraphModuleId, to: GraphModuleId): Edge = from -> to
}
