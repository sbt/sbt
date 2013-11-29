/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import java.io.File
import java.net.{ URI, URL }
import scala.xml.NodeSeq
import org.apache.ivy.plugins.resolver.{ DependencyResolver, IBiblioResolver }
import org.apache.ivy.util.url.CredentialsStore
import org.apache.ivy.core.module.descriptor
import org.apache.ivy.util.filter.{Filter => IvyFilter}
import sbt.serialization._

/** Additional information about a project module */
final case class ModuleInfo(nameFormal: String, description: String = "", homepage: Option[URL] = None, startYear: Option[Int] = None, licenses: Seq[(String, URL)] = Nil, organizationName: String = "", organizationHomepage: Option[URL] = None, scmInfo: Option[ScmInfo] = None, developers: Seq[Developer] = Seq()) {
  def this(nameFormal: String, description: String, homepage: Option[URL], startYear: Option[Int], licenses: Seq[(String, URL)], organizationName: String, organizationHomepage: Option[URL], scmInfo: Option[ScmInfo]) =
    this(nameFormal, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo, Seq())
  def formally(name: String) = copy(nameFormal = name)
  def describing(desc: String, home: Option[URL]) = copy(description = desc, homepage = home)
  def licensed(lics: (String, URL)*) = copy(licenses = lics)
  def organization(name: String, home: Option[URL]) = copy(organizationName = name, organizationHomepage = home)
}

/** Basic SCM information for a project module */
final case class ScmInfo(browseUrl: URL, connection: String, devConnection: Option[String] = None)

final case class Developer(id: String, name: String, email: String, url: URL)

/** Rule to either:
  * <ul>
  * <li> exclude unwanted dependencies pulled in transitively by a module, or to</li>
  * <li> include and merge artifacts coming from the ModuleDescriptor if "dependencyArtifacts" are also provided.</li>
  * </ul>
  * Which one depends on the parameter name which it is passed to, but the filter has the same fields in both cases. */
final case class InclExclRule(organization: String = "*", name: String = "*", artifact: String = "*", configurations: Seq[String] = Nil)
object InclExclRule {
  def everything = InclExclRule("*", "*", "*", Nil)

  implicit val pickler: Pickler[InclExclRule] with Unpickler[InclExclRule] = PicklerUnpickler.generate[InclExclRule]
}

/** Work around the inadequacy of Ivy's ArtifactTypeFilter (that it cannot reverse a filter)
  * @param types represents the artifact types that we should try to resolve for (as in the allowed values of
  *              `artifact[type]` from a dependency `<publications>` section). One can use this to filter
  *              source / doc artifacts.
  * @param inverted whether to invert the types filter (i.e. allow only types NOT in the set) */
case class ArtifactTypeFilter(types: Set[String], inverted: Boolean) {
  def invert = copy(inverted = !inverted)
  def apply(a: descriptor.Artifact): Boolean = (types contains a.getType) ^ inverted
}

object ArtifactTypeFilter {
	def allow(types: Set[String])  = ArtifactTypeFilter(types, false)
	def forbid(types: Set[String]) = ArtifactTypeFilter(types, true)

	implicit def toIvyFilter(f: ArtifactTypeFilter): IvyFilter = new IvyFilter {
		override def accept(o: Object): Boolean = Option(o) exists { case a: descriptor.Artifact => f.apply(a) }
	}
}

final case class ModuleConfiguration(organization: String, name: String, revision: String, resolver: Resolver)
object ModuleConfiguration {
  def apply(org: String, resolver: Resolver): ModuleConfiguration = apply(org, "*", "*", resolver)
  def apply(org: String, name: String, resolver: Resolver): ModuleConfiguration = ModuleConfiguration(org, name, "*", resolver)
}

final case class ConflictManager(name: String, organization: String = "*", module: String = "*")

/** See http://ant.apache.org/ivy/history/latest-milestone/settings/conflict-managers.html for details of the different conflict managers.*/
object ConflictManager {
  val all = ConflictManager("all")
  val latestTime = ConflictManager("latest-time")
  val latestRevision = ConflictManager("latest-revision")
  val latestCompatible = ConflictManager("latest-compatible")
  val strict = ConflictManager("strict")
  val default = latestRevision
}
