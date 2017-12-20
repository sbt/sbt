/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File
import java.net.URI

import sbt.io.IO

// in all of these, the URI must be resolved and normalized before it is definitive

/** Identifies a project or build. */
sealed trait Reference

/** A fully resolved, unique identifier for a project or build. */
sealed trait ResolvedReference extends Reference

/** Identifies a build. */
sealed trait BuildReference extends Reference

/** Identifies the build for the current context. */
final case object ThisBuild extends BuildReference

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
final case object LocalRootProject extends ProjectReference

/** Identifies the project for the current context. */
final case object ThisProject extends ProjectReference

object ProjectRef {
  def apply(base: File, id: String): ProjectRef = ProjectRef(IO toURI base, id)
}
object RootProject {

  /** Reference to the root project at 'base'.*/
  def apply(base: File): RootProject = RootProject(IO toURI base)
}
object Reference {
  implicit val resolvedReferenceOrdering: Ordering[ResolvedReference] =
    new Ordering[ResolvedReference] {
      def compare(a: ResolvedReference, b: ResolvedReference): Int = (a, b) match {
        case (ba: BuildRef, bb: BuildRef)     => buildRefOrdering.compare(ba, bb)
        case (pa: ProjectRef, pb: ProjectRef) => projectRefOrdering.compare(pa, pb)
        case (_: BuildRef, _: ProjectRef)     => -1
        case (_: ProjectRef, _: BuildRef)     => 1
      }
    }
  implicit val buildRefOrdering: Ordering[BuildRef] = new Ordering[BuildRef] {
    def compare(a: BuildRef, b: BuildRef): Int = a.build compareTo b.build
  }

  implicit val projectRefOrdering: Ordering[ProjectRef] = new Ordering[ProjectRef] {
    def compare(a: ProjectRef, b: ProjectRef): Int = {
      val bc = a.build compareTo b.build
      if (bc == 0) a.project compareTo b.project else bc
    }
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

  /** Extracts the build URI from a Reference if one has been explicitly defined.*/
  def uri(ref: Reference): Option[URI] = ref match {
    case RootProject(b)   => Some(b)
    case ProjectRef(b, _) => Some(b)
    case BuildRef(b)      => Some(b)
    case _                => None
  }
}
