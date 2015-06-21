package coursier.core

import scalaz._

object Xml {

  /** A representation of an XML node/document, with different implementations on the JVM and JS */
  trait Node {
    def label: String
    def child: Seq[Node]
    def isText: Boolean
    def textContent: String
    def isElement: Boolean
  }

  object Node {
    val empty: Node =
      new Node {
        val isText = false
        val isElement = false
        val child = Nil
        val label = ""
        val textContent = ""
      }
  }

  object Text {
    def unapply(n: Node): Option[String] =
      if (n.isText) Some(n.textContent)
      else None
  }

  private def text(elem: Node, label: String, description: String) = {
    import Scalaz.ToOptionOpsFromOption

    elem.child
      .find(_.label == label)
      .flatMap(_.child.collectFirst{case Text(t) => t})
      .toRightDisjunction(s"$description not found")
  }

  def property(elem: Node): String \/ (String, String) = {
    // Not matching with Text, which fails on scala-js if the property value has xml comments
    if (elem.isElement) \/-(elem.label -> elem.textContent)
    else -\/(s"Can't parse property $elem")
  }

  // TODO Allow no version in some contexts
  private def module(node: Node, groupIdIsOptional: Boolean = false): String \/ Module = {
    for {
      organization <- {
        val e = text(node, "groupId", "Organization")
        if (groupIdIsOptional) e.orElse(\/-(""))
        else e
      }
      name <- text(node, "artifactId", "Name")
    } yield Module(organization, name).trim
  }

  private def readVersion(node: Node) =
    text(node, "version", "Version").getOrElse("").trim

  private val defaultScope = Scope.Other("")
  private val defaultType = "jar"
  private val defaultClassifier = ""

  def dependency(node: Node): String \/ Dependency = {
    for {
      mod <- module(node)
      version0 = readVersion(node)
      scopeOpt = text(node, "scope", "").toOption
        .map(Parse.scope)
      typeOpt = text(node, "type", "").toOption
      classifierOpt = text(node, "classifier", "").toOption
      xmlExclusions = node.child
        .find(_.label == "exclusions")
        .map(_.child.filter(_.label == "exclusion"))
        .getOrElse(Seq.empty)
      exclusions <- {
        import Scalaz._
        xmlExclusions.toList.traverseU(module(_))
      }
      optional = text(node, "optional", "").toOption.toSeq.contains("true")
    } yield Dependency(
        mod,
        version0,
        scopeOpt getOrElse defaultScope,
        typeOpt getOrElse defaultType,
        classifierOpt getOrElse defaultClassifier,
        exclusions.map(mod => (mod.organization, mod.name)).toSet,
        optional
      )
  }

  private def profileActivation(node: Node): (Option[Boolean], Activation) = {
    val byDefault =
      text(node, "activeByDefault", "").toOption.flatMap{
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }

    val properties = node.child
      .filter(_.label == "property")
      .flatMap{ p =>
        for{
          name <- text(p, "name", "").toOption
          valueOpt = text(p, "value", "").toOption
        } yield (name, valueOpt)
      }

    (byDefault, Activation(properties))
  }

  def profile(node: Node): String \/ Profile = {
    import Scalaz._

    for {
      id <- text(node, "id", "Profile ID")

      xmlActivationOpt = node.child
        .find(_.label == "activation")
      (activeByDefault, activation) = xmlActivationOpt.fold((Option.empty[Boolean], Activation(Nil)))(profileActivation)

      xmlDeps = node.child
        .find(_.label == "dependencies")
        .map(_.child.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      deps <- xmlDeps.toList.traverseU(dependency)

      xmlDepMgmts = node.child
        .find(_.label == "dependencyManagement")
        .flatMap(_.child.find(_.label == "dependencies"))
        .map(_.child.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      depMgmts <- xmlDepMgmts.toList.traverseU(dependency)

      xmlProperties = node.child
        .find(_.label == "properties")
        .map(_.child.collect{case elem if elem.isElement => elem})
        .getOrElse(Seq.empty)
      properties <- {
        import Scalaz._
        xmlProperties.toList.traverseU(property)
      }

    } yield Profile(id, activeByDefault, activation, deps, depMgmts, properties.toMap)
  }

  def project(pom: Node): String \/ Project = {
    import Scalaz._

    for {
      projModule <- module(pom, groupIdIsOptional = true)
      projVersion = readVersion(pom)

      parentOpt = pom.child
        .find(_.label == "parent")
      parentModuleOpt <- parentOpt
        .map(module(_).map(Some(_)))
        .getOrElse(\/-(None))
      parentVersionOpt = parentOpt
        .map(readVersion)

      xmlDeps = pom.child
        .find(_.label == "dependencies")
        .map(_.child.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      deps <- xmlDeps.toList.traverseU(dependency)

      xmlDepMgmts = pom.child
        .find(_.label == "dependencyManagement")
        .flatMap(_.child.find(_.label == "dependencies"))
        .map(_.child.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      depMgmts <- xmlDepMgmts.toList.traverseU(dependency)

      groupId <- Some(projModule.organization).filter(_.nonEmpty)
        .orElse(parentModuleOpt.map(_.organization).filter(_.nonEmpty))
        .toRightDisjunction("No organization found")
      version <- Some(projVersion).filter(_.nonEmpty)
        .orElse(parentVersionOpt.filter(_.nonEmpty))
        .toRightDisjunction("No version found")

      _ <- parentVersionOpt
        .map(v => if (v.isEmpty) -\/("Parent version missing") else \/-(()))
        .getOrElse(\/-(()))
      _ <- parentModuleOpt
        .map(mod => if (mod.organization.isEmpty) -\/("Parent organization missing") else \/-(()))
        .getOrElse(\/-(()))

      xmlProperties = pom.child
        .find(_.label == "properties")
        .map(_.child.collect{case elem if elem.isElement => elem})
        .getOrElse(Seq.empty)
      properties <- xmlProperties.toList.traverseU(property)

      xmlProfiles = pom.child
        .find(_.label == "profiles")
        .map(_.child.filter(_.label == "profile"))
        .getOrElse(Seq.empty)
      profiles <- xmlProfiles.toList.traverseU(profile)

    } yield Project(
      projModule.copy(organization = groupId),
      version,
      deps,
      parentModuleOpt.map((_, parentVersionOpt.getOrElse(""))),
      depMgmts,
      properties.toMap,
      profiles
    )
  }

  def versions(node: Node): String \/ Versions = {
    import Scalaz._

    for {
      organization <- text(node, "groupId", "Organization") // Ignored
      name <- text(node, "artifactId", "Name") // Ignored

      xmlVersioning <- node.child
        .find(_.label == "versioning")
        .toRightDisjunction("Versioning info not found in metadata")

      latest = text(xmlVersioning, "latest", "Latest version")
        .getOrElse("")
      release = text(xmlVersioning, "release", "Release version")
        .getOrElse("")

      versions <- xmlVersioning.child
        .find(_.label == "versions")
        .map(_.child.filter(_.label == "version").flatMap(_.child.collectFirst{case Text(t) => t}))
        .toRightDisjunction("Version list not found in metadata")

      lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .filter(s => s.length == 14 && s.forall(_.isDigit))
        .map(s => Versions.DateTime(
          s.substring(0, 4).toInt,
          s.substring(4, 6).toInt,
          s.substring(6, 8).toInt,
          s.substring(8, 10).toInt,
          s.substring(10, 12).toInt,
          s.substring(12, 14).toInt
        ))

    } yield Versions(latest, release, versions.toList, lastUpdatedOpt)
  }

}
