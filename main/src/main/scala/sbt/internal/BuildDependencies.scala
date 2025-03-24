/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.Types.idFun
import sbt.internal.util.Dag
import BuildDependencies.*

final class BuildDependencies private (
    val classpath: DependencyMap[ClasspathDep[ProjectRef]],
    val aggregate: DependencyMap[ProjectRef]
) {
  def classpathRefs(ref: ProjectRef): Seq[ProjectRef] = classpath(ref) map getID
  def classpathTransitiveRefs(ref: ProjectRef): Seq[ProjectRef] = classpathTransitive(ref)

  lazy val classpathTransitive: DependencyMap[ProjectRef] = transitive(classpath, getID)
  lazy val aggregateTransitive: DependencyMap[ProjectRef] =
    transitive(aggregate, idFun[ProjectRef])

  def addClasspath(ref: ProjectRef, deps: ClasspathDep[ProjectRef]*): BuildDependencies =
    new BuildDependencies(classpath.updated(ref, deps ++ classpath.getOrElse(ref, Nil)), aggregate)
  def addAggregate(ref: ProjectRef, deps: ProjectRef*): BuildDependencies =
    new BuildDependencies(classpath, aggregate.updated(ref, deps ++ aggregate.getOrElse(ref, Nil)))
}
object BuildDependencies {
  def apply(
      classpath: DependencyMap[ClasspathDep[ProjectRef]],
      aggregate: DependencyMap[ProjectRef]
  ): BuildDependencies =
    new BuildDependencies(classpath, aggregate)

  type DependencyMap[D] = Map[ProjectRef, Seq[D]]
  def transitive[D](deps: DependencyMap[D], extract: D => ProjectRef): DependencyMap[ProjectRef] =
    for ((ref, _) <- deps) yield {
      val sorted = Dag.topologicalSort(ref)(d => deps(d) map extract)
      (ref, sorted dropRight 1)
    }
  val getID: ClasspathDep[ProjectRef] => ProjectRef = _.project
}
