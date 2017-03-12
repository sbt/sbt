/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.{ URI, URL }
import scala.xml.NodeSeq
import org.apache.ivy.plugins.resolver.{ DependencyResolver, IBiblioResolver }
import org.apache.ivy.util.url.CredentialsStore
import sbt.serialization._

/** Additional information about a project module */
final case class ModuleInfo(nameFormal: String,
                            description: String = "",
                            homepage: Option[URL] = None,
                            startYear: Option[Int] = None,
                            licenses: Seq[(String, URL)] = Nil,
                            organizationName: String = "",
                            organizationHomepage: Option[URL] = None,
                            scmInfo: Option[ScmInfo] = None,
                            developers: Seq[Developer] = Seq()) {
  def this(nameFormal: String,
           description: String,
           homepage: Option[URL],
           startYear: Option[Int],
           licenses: Seq[(String, URL)],
           organizationName: String,
           organizationHomepage: Option[URL],
           scmInfo: Option[ScmInfo]) =
    this(
      nameFormal,
      description,
      homepage,
      startYear,
      licenses,
      organizationName,
      organizationHomepage,
      scmInfo,
      Seq()
    )
  def formally(name: String) = copy(nameFormal = name)
  def describing(desc: String, home: Option[URL]) = copy(description = desc, homepage = home)
  def licensed(lics: (String, URL)*) = copy(licenses = lics)
  def organization(name: String, home: Option[URL]) =
    copy(organizationName = name, organizationHomepage = home)
}

/** Basic SCM information for a project module */
final case class ScmInfo(browseUrl: URL, connection: String, devConnection: Option[String] = None)

final case class Developer(id: String, name: String, email: String, url: URL)

/** Rule to exclude unwanted dependencies pulled in transitively by a module. */
final case class ExclusionRule(organization: String = "*",
                               name: String = "*",
                               artifact: String = "*",
                               configurations: Seq[String] = Nil)
object ExclusionRule {
  implicit val pickler: Pickler[ExclusionRule] with Unpickler[ExclusionRule] =
    PicklerUnpickler.generate[ExclusionRule]
}

final case class ModuleConfiguration(organization: String, name: String, revision: String, resolver: Resolver)
object ModuleConfiguration {
  def apply(org: String, resolver: Resolver): ModuleConfiguration = apply(org, "*", "*", resolver)
  def apply(org: String, name: String, resolver: Resolver): ModuleConfiguration =
    ModuleConfiguration(org, name, "*", resolver)
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
