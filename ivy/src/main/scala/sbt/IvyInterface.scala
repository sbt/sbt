/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import java.io.File
import java.net.{URI, URL}
import scala.xml.NodeSeq
import org.apache.ivy.plugins.resolver.{DependencyResolver, IBiblioResolver}
import org.apache.ivy.util.url.CredentialsStore

/** Additional information about a project module */
case class ModuleInfo(nameFormal: String, description: String = "", homepage: Option[URL] = None, startYear: Option[Int] = None, licenses: Seq[(String, URL)] = Nil, organizationName: String = "", organizationHomepage: Option[URL] = None, scmInfo: Option[ScmInfo] = None)
{
	def formally(name: String) = copy(nameFormal = name)
	def describing(desc: String, home: Option[URL]) = copy(description = desc, homepage = home)
	def licensed(lics: (String, URL)*) = copy(licenses = lics)
	def organization(name: String, home: Option[URL]) = copy(organizationName = name, organizationHomepage = home)
}

/** Basic SCM information for a project module */
case class ScmInfo(browseUrl: URL, connection: String, devConnection: Option[String] = None)

/** Rule to exclude unwanted dependencies pulled in transitively by a module. */
case class ExclusionRule(organization: String = "*", name: String = "*", artifact: String = "*", configurations: Seq[String] = Nil)

final case class ModuleConfiguration(organization: String, name: String, revision: String, resolver: Resolver)
object ModuleConfiguration
{
	def apply(org: String, resolver: Resolver): ModuleConfiguration = apply(org, "*", "*", resolver)
	def apply(org: String, name: String, resolver: Resolver): ModuleConfiguration = ModuleConfiguration(org, name, "*", resolver)
}
