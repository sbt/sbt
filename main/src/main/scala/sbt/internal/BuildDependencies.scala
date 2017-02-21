package sbt
package internal

import sbt.internal.util.Types.idFun
import sbt.internal.util.Dag
import BuildDependencies._

final class BuildDependencies private (val classpath: DependencyMap[ClasspathDep[ProjectRef]], val aggregate: DependencyMap[ProjectRef]) {
  def classpathRefs(ref: ProjectRef): Seq[ProjectRef] = classpath(ref) map getID
  def classpathTransitiveRefs(ref: ProjectRef): Seq[ProjectRef] = classpathTransitive(ref)

  lazy val classpathTransitive: DependencyMap[ProjectRef] = transitive(classpath, getID)
  lazy val aggregateTransitive: DependencyMap[ProjectRef] = transitive(aggregate, idFun[ProjectRef])

  def addClasspath(ref: ProjectRef, deps: ClasspathDep[ProjectRef]*): BuildDependencies =
    new BuildDependencies(classpath.updated(ref, deps.toVector ++ classpath.getOrElse(ref, Vector.empty)), aggregate)
  def addAggregate(ref: ProjectRef, deps: ProjectRef*): BuildDependencies =
    new BuildDependencies(classpath, aggregate.updated(ref, deps.toVector ++ aggregate.getOrElse(ref, Vector.empty)))
}
object BuildDependencies {
  def apply(classpath: DependencyMap[ClasspathDep[ProjectRef]], aggregate: DependencyMap[ProjectRef]): BuildDependencies =
    new BuildDependencies(classpath, aggregate)

  type DependencyMap[D] = Map[ProjectRef, Vector[D]]
  def transitive[D](deps: DependencyMap[D], extract: D => ProjectRef): DependencyMap[ProjectRef] =
    for ((ref, _) <- deps) yield {
      val sorted = Dag.topologicalSort(ref)(d => deps(d) map extract)
      (ref, sorted.toVector dropRight 1)
    }
  val getID: ClasspathDep[ProjectRef] => ProjectRef = _.project
}
