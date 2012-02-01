/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI

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
final case class ProjectRef(build: URI, project: String) extends ProjectReference with ResolvedReference
/** Identifies a project in the current build context. */
final case class LocalProject(project: String) extends ProjectReference
/** Identifies the root project in the specified build. */
final case class RootProject(build: URI) extends ProjectReference
/** Identifies the root project in the current build context. */
final case object LocalRootProject extends ProjectReference
/** Identifies the project for the current context. */
final case object ThisProject extends ProjectReference

object ProjectRef
{
	def apply(base: File, id: String): ProjectRef = ProjectRef(IO toURI base, id)
}
object RootProject
{
	/** Reference to the root project at 'base'.*/
	def apply(base: File): RootProject = RootProject(IO toURI base)
}
object Reference
{
	def buildURI(ref: ResolvedReference): URI = ref match {
		case BuildRef(b) => b
		case ProjectRef(b, _) => b
	}	
	/** Extracts the build URI from a Reference if one has been explicitly defined.*/
	def uri(ref: Reference): Option[URI] = ref match {
		case RootProject(b) => Some(b)
		case ProjectRef(b, _) => Some(b)
		case BuildRef(b) => Some(b)
		case _ => None
	}
	implicit def uriToRef(u: URI): ProjectReference = RootProject(u)
	implicit def fileToRef(f: File): ProjectReference = RootProject(f)
	implicit def stringToReference(s: String): ProjectReference = LocalProject(s)
	implicit def projectToRef(p: Project): ProjectReference = LocalProject(p.id)
}