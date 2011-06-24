/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI

// in all of these, the URI must be resolved and normalized before it is definitive

sealed trait Reference
sealed trait ResolvedReference extends Reference

sealed trait BuildReference extends Reference
final case object ThisBuild extends BuildReference
final case class BuildRef(build: URI) extends BuildReference with ResolvedReference

sealed trait ProjectReference extends Reference
final case class ProjectRef(build: URI, project: String) extends ProjectReference with ResolvedReference
final case class LocalProject(project: String) extends ProjectReference
final case class RootProject(build: URI) extends ProjectReference
final case object LocalRootProject extends ProjectReference
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