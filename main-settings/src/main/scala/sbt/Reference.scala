/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import java.net.URI

import sbt.internal.util.AttributeKey
import sbt.io.IO
import sbt.librarymanagement.Configuration
import sbt.SlashSyntax.RichConfiguration

// in all of these, the URI must be resolved and normalized before it is definitive

/** Identifies a project or build. */
sealed trait Reference:
  private[sbt] def asScopeAxis: ScopeAxis[this.type] =
    Select(this)
  private[sbt] def asScope: Scope =
    Scope(asScopeAxis, This, This, This)

  def /(c: ConfigKey): RichConfiguration = RichConfiguration(asScope.rescope(c))

  def /(c: Configuration): RichConfiguration = RichConfiguration(asScope.rescope(c))

  // This is for handling `Zero / Zero / name`.
  def /(configAxis: ScopeAxis[ConfigKey]): RichConfiguration =
    new RichConfiguration(asScope.copy(config = configAxis))

  final def /[K](key: Scoped.ScopingSetting[K]): K = key.rescope(asScope)

  final def /(key: AttributeKey[?]): Scope = asScope.rescope(key)
end Reference

/** A fully resolved, unique identifier for a project or build. */
sealed trait ResolvedReference extends Reference

/** Identifies a build. */
sealed trait BuildReference extends Reference

/** Identifies the build for the current context. */
case object ThisBuild extends BuildReference

/** Uniquely identifies a build by a URI. */
final case class BuildRef(build: URI) extends BuildReference with ResolvedReference

/** Identifies a project. */
sealed trait ProjectReference extends Reference

/** Uniquely references a project by a URI and a project identifier String. */
final case class ProjectRef(build: URI, project: String)
    extends ProjectReference
    with ResolvedReference

/** Identifies a project in the current build context. */
final case class LocalProject(project: String) extends ProjectReference

/** Identifies the root project in the specified build. */
final case class RootProject(build: URI) extends ProjectReference

/** Identifies the root project in the current build context. */
case object LocalRootProject extends ProjectReference

/** Identifies the project for the current context. */
case object ThisProject extends ProjectReference

object ProjectRef {
  def apply(base: File, id: String): ProjectRef = ProjectRef(IO toURI base, id)
}
object RootProject {

  /** Reference to the root project at 'base'. */
  def apply(base: File): RootProject = RootProject(IO toURI base)
}
object Reference {
  given resolvedReferenceOrdering: Ordering[ResolvedReference] = {
    case (ba: BuildRef, bb: BuildRef)     => buildRefOrdering.compare(ba, bb)
    case (pa: ProjectRef, pb: ProjectRef) => projectRefOrdering.compare(pa, pb)
    case (_: BuildRef, _: ProjectRef)     => -1
    case (_: ProjectRef, _: BuildRef)     => 1
  }
  given buildRefOrdering: Ordering[BuildRef] = (a, b) => a.build.compareTo(b.build)

  given projectRefOrdering: Ordering[ProjectRef] = (a, b) => {
    val bc = a.build.compareTo(b.build)
    if bc == 0 then a.project.compareTo(b.project) else bc
  }

  def display(ref: Reference): String =
    ref match {
      case pr: ProjectReference => display(pr)
      case br: BuildReference   => display(br)
    }

  def display(ref: BuildReference): String =
    ref match {
      case ThisBuild     => "{<this>}"
      case BuildRef(uri) => "{" + uri + "}"
    }
  def display(ref: ProjectReference): String =
    ref match {
      case ThisProject         => "{<this>}<this>"
      case LocalRootProject    => "{<this>}<root>"
      case LocalProject(id)    => "{<this>}" + id
      case RootProject(uri)    => "{" + uri + " }<root>"
      case ProjectRef(uri, id) => s"""ProjectRef(uri("$uri"), "$id")"""
    }

  def buildURI(ref: ResolvedReference): URI = ref match {
    case BuildRef(b)      => b
    case ProjectRef(b, _) => b
  }

  /** Extracts the build URI from a Reference if one has been explicitly defined. */
  def uri(ref: Reference): Option[URI] = ref match {
    case RootProject(b)   => Some(b)
    case ProjectRef(b, _) => Some(b)
    case BuildRef(b)      => Some(b)
    case _                => None
  }
}
