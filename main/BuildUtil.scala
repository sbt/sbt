package sbt

import java.net.URI

final class BuildUtil[Proj](
	val keyIndex: KeyIndex,
	val data: Settings[Scope],
	val root: URI,
	val rootProjectID: URI => String,
	val project: (URI, String) => Proj,
	val configurations: Proj => Seq[ConfigKey],
	val aggregates: Relation[ProjectRef, ProjectRef]
)
{
	def rootProject(uri: URI): Proj =
		project(uri, rootProjectID(uri))

	def resolveRef(ref: Reference): ResolvedReference =
		Scope.resolveReference(root, rootProjectID, ref)

	def projectFor(ref: ResolvedReference): Proj = ref match {
		case ProjectRef(uri, id) => project(uri, id)
		case BuildRef(uri) => rootProject(uri)
	}
	def projectRefFor(ref: ResolvedReference): ProjectRef = ref match {
		case p: ProjectRef => p
		case BuildRef(uri) => ProjectRef(uri, rootProjectID(uri))
	}
	def projectForAxis(ref: Option[ResolvedReference]): Proj = ref match {
		case Some(ref) => projectFor(ref)
		case None => rootProject(root)
	}
	def exactProject(refOpt: Option[Reference]): Option[Proj] = refOpt map resolveRef flatMap {
		case ProjectRef(uri, id) => Some(project(uri, id))
		case _ => None
	}

	val configurationsForAxis: Option[ResolvedReference] => Seq[String] = 
		refOpt => configurations(projectForAxis(refOpt)).map(_.name)
}