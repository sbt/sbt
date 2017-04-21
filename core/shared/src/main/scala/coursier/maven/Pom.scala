package coursier.maven

import coursier.core._

import scalaz._

object Pom {
  import coursier.util.Xml._

  /**
    * Returns either a property's key-value pair or an error if the elem is not an element.
    *
    * This method trims all spaces, whereas Maven has an option to preserve them.
    *
    * @param elem a property element
    * @return the key and the value of the property
    * @see [[https://issues.apache.org/jira/browse/MNG-5380]]
    */
  def property(elem: Node): String \/ (String, String) = {
    // Not matching with Text, which fails on scala-js if the property value has xml comments
    if (elem.isElement) \/-(elem.label -> elem.textContent.trim)
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
    } yield Module(organization, name, Map.empty).trim
  }

  private def readVersion(node: Node) =
    text(node, "version", "Version").getOrElse("").trim

  def dependency(node: Node): String \/ (String, Dependency) = {
    for {
      mod <- module(node)
      version0 = readVersion(node)
      scopeOpt = text(node, "scope", "").toOption
      typeOpt = text(node, "type", "").toOption
      classifierOpt = text(node, "classifier", "").toOption
      xmlExclusions = node.children
        .find(_.label == "exclusions")
        .map(_.children.filter(_.label == "exclusion"))
        .getOrElse(Seq.empty)
      exclusions <- {
        import Scalaz._
        xmlExclusions.toList.traverseU(module(_))
      }
      optional = text(node, "optional", "").toOption.toSeq.contains("true")
    } yield scopeOpt.getOrElse("") -> Dependency(
        mod,
        version0,
        "",
        exclusions.map(mod => (mod.organization, mod.name)).toSet,
        Attributes(typeOpt.getOrElse(""), classifierOpt.getOrElse("")),
        optional,
        transitive = true
      )
  }

  private def profileActivation(node: Node): (Option[Boolean], Activation) = {
    val byDefault =
      text(node, "activeByDefault", "").toOption.flatMap{
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }

    val properties = node.children
      .filter(_.label == "property")
      .flatMap{ p =>
        for{
          name <- text(p, "name", "").toOption
          valueOpt = text(p, "value", "").toOption
        } yield (name, valueOpt)
      }

    val osNodeOpt = node.children.collectFirst { case n if n.label == "os" => n }

    val os = Activation.Os(
      osNodeOpt.flatMap(n => text(n, "arch", "").toOption),
      osNodeOpt.flatMap(n => text(n, "family", "").toOption).toSet,
      osNodeOpt.flatMap(n => text(n, "name", "").toOption),
      osNodeOpt.flatMap(n => text(n, "version", "").toOption)
    )

    val jdk = text(node, "jdk", "").toOption.flatMap { s =>
      Parse.versionInterval(s).map(-\/(_))
        .orElse(Parse.version(s).map(v => \/-(Seq(v))))
    }

    val activation = Activation(properties, os, jdk)

    (byDefault, activation)
  }

  def profile(node: Node): String \/ Profile = {
    import Scalaz._

    val id = text(node, "id", "Profile ID").getOrElse("")

    val xmlActivationOpt = node.children
      .find(_.label == "activation")
    val (activeByDefault, activation) = xmlActivationOpt.fold((Option.empty[Boolean], Activation.empty))(profileActivation)

    val xmlDeps = node.children
      .find(_.label == "dependencies")
      .map(_.children.filter(_.label == "dependency"))
      .getOrElse(Seq.empty)

    for {
      deps <- xmlDeps.toList.traverseU(dependency)

      xmlDepMgmts = node.children
        .find(_.label == "dependencyManagement")
        .flatMap(_.children.find(_.label == "dependencies"))
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      depMgmts <- xmlDepMgmts.toList.traverseU(dependency)

      xmlProperties = node.children
        .find(_.label == "properties")
        .map(_.children.collect{case elem if elem.isElement => elem})
        .getOrElse(Seq.empty)

      properties <- {
        import Scalaz._
        xmlProperties.toList.traverseU(property)
      }

    } yield Profile(id, activeByDefault, activation, deps, depMgmts, properties.toMap)
  }

  def packagingOpt(pom: Node): Option[String] =
    text(pom, "packaging", "").toOption

  def project(pom: Node): String \/ Project = {
    import Scalaz._

    for {
      projModule <- module(pom, groupIdIsOptional = true)
      projVersion = readVersion(pom)

      parentOpt = pom.children
        .find(_.label == "parent")
      parentModuleOpt <- parentOpt
        .map(module(_).map(Some(_)))
        .getOrElse(\/-(None))
      parentVersionOpt = parentOpt
        .map(readVersion)

      xmlDeps = pom.children
        .find(_.label == "dependencies")
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
      deps <- xmlDeps.toList.traverseU(dependency)

      xmlDepMgmts = pom.children
        .find(_.label == "dependencyManagement")
        .flatMap(_.children.find(_.label == "dependencies"))
        .map(_.children.filter(_.label == "dependency"))
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

      xmlProperties = pom.children
        .find(_.label == "properties")
        .map(_.children.collect{case elem if elem.isElement => elem})
        .getOrElse(Seq.empty)
      properties <- xmlProperties.toList.traverseU(property)

      xmlProfiles = pom.children
        .find(_.label == "profiles")
        .map(_.children.filter(_.label == "profile"))
        .getOrElse(Seq.empty)
      profiles <- xmlProfiles.toList.traverseU(profile)

      extraAttrs <- properties
        .collectFirst { case ("extraDependencyAttributes", s) => extraAttributes(s) }
        .getOrElse(\/-(Map.empty))

      extraAttrsMap = extraAttrs.map {
        case (mod, ver) =>
          (mod.copy(attributes = Map.empty), ver) -> mod.attributes
      }.toMap

    } yield {

      val description = pom.children
        .find(_.label == "description")
        .map(_.textContent)
        .getOrElse("")

      val homePage = pom.children
        .find(_.label == "url")
        .map(_.textContent)
        .getOrElse("")

      val licenses = pom.children
        .find(_.label == "licenses")
        .toSeq
        .flatMap(_.children)
        .filter(_.label == "license")
        .flatMap { n =>
          text(n, "name", "License name").toOption.map { name =>
            (name, text(n, "url", "License URL").toOption)
          }.toSeq
        }

      val developers = pom.children
        .find(_.label == "developers")
        .toSeq
        .flatMap(_.children)
        .filter(_.label == "developer")
        .map { n =>
          for {
            id <- text(n, "id", "Developer ID")
            name <- text(n, "name", "Developer name")
            url <- text(n, "url", "Developer URL")
          } yield Info.Developer(id, name, url)
        }
        .collect {
          case \/-(d) => d
        }

      Project(
        projModule.copy(organization = groupId),
        version,
        deps.map {
          case (config, dep0) =>
            val dep = extraAttrsMap.get(dep0.moduleVersion).fold(dep0)(attrs =>
              dep0.copy(module = dep0.module.copy(attributes = attrs))
            )
            config -> dep
        },
        Map.empty,
        parentModuleOpt.map((_, parentVersionOpt.getOrElse(""))),
        depMgmts,
        properties,
        profiles,
        None,
        None,
        packagingOpt(pom),
        None,
        Nil,
        Info(
          description,
          homePage,
          licenses,
          developers,
          None
        )
      )
    }
  }

  def versions(node: Node): String \/ Versions = {
    import Scalaz._

    for {
      organization <- text(node, "groupId", "Organization") // Ignored
      name <- text(node, "artifactId", "Name") // Ignored

      xmlVersioning <- node.children
        .find(_.label == "versioning")
        .toRightDisjunction("Versioning info not found in metadata")

      latest = text(xmlVersioning, "latest", "Latest version")
        .getOrElse("")
      release = text(xmlVersioning, "release", "Release version")
        .getOrElse("")

      versionsOpt = xmlVersioning.children
        .find(_.label == "versions")
        .map(_.children.filter(_.label == "version").flatMap(_.children.collectFirst{case Text(t) => t}))

      lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .flatMap(parseDateTime)

    } yield Versions(latest, release, versionsOpt.map(_.toList).getOrElse(Nil), lastUpdatedOpt)
  }

  def snapshotVersion(node: Node): String \/ SnapshotVersion = {
    def textOrEmpty(name: String, desc: String) =
      text(node, name, desc)
        .toOption
        .getOrElse("")

    val classifier = textOrEmpty("classifier", "Classifier")
    val ext = textOrEmpty("extension", "Extensions")
    val value = textOrEmpty("value", "Value")

    val updatedOpt = text(node, "updated", "Updated")
      .toOption
      .flatMap(parseDateTime)

    \/-(SnapshotVersion(
      classifier,
      ext,
      value,
      updatedOpt
    ))
  }

  /** If `snapshotVersion` is missing, guess it based on
    * `version`, `timestamp` and `buildNumber`, as is done in:
    * https://github.com/sbt/ivy/blob/2.3.x-sbt/src/java/org/apache/ivy/plugins/resolver/IBiblioResolver.java
    */
  def guessedSnapshotVersion(
    version: String,
    timestamp: String,
    buildNumber: Int
  ): SnapshotVersion = {
    val value = s"${version.dropRight("SNAPSHOT".length)}$timestamp-$buildNumber"
    SnapshotVersion("*", "*", value, None)
  }

  def snapshotVersioning(node: Node): String \/ SnapshotVersioning = {
    import Scalaz._

    // FIXME Quite similar to Versions above
    for {
      organization <- text(node, "groupId", "Organization")
      name <- text(node, "artifactId", "Name")
      version = readVersion(node)

      xmlVersioning <- node.children
        .find(_.label == "versioning")
        .toRightDisjunction("Versioning info not found in metadata")

      latest = text(xmlVersioning, "latest", "Latest version")
        .getOrElse("")
      release = text(xmlVersioning, "release", "Release version")
        .getOrElse("")

      versionsOpt = xmlVersioning.children
        .find(_.label == "versions")
        .map(_.children.filter(_.label == "version").flatMap(_.children.collectFirst{case Text(t) => t}))

      lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .toOption
        .flatMap(parseDateTime)

      xmlSnapshotOpt = xmlVersioning.children
        .find(_.label == "snapshot")

      timestamp = xmlSnapshotOpt
        .flatMap(
          text(_, "timestamp", "Snapshot timestamp")
            .toOption
        )
        .getOrElse("")

      buildNumber = xmlSnapshotOpt
        .flatMap(
          text(_, "buildNumber", "Snapshot build number")
            .toOption
        )
        .filter(s => s.nonEmpty && s.forall(_.isDigit))
        .map(_.toInt)

      localCopy = xmlSnapshotOpt
        .flatMap(
          text(_, "localCopy", "Snapshot local copy")
            .toOption
        )
        .collect{
          case "true" => true
          case "false" => false
        }

      xmlSnapshotVersions = xmlVersioning.children
        .find(_.label == "snapshotVersions")
        .map(_.children.filter(_.label == "snapshotVersion"))
        .getOrElse(Seq.empty)
      snapshotVersions <- xmlSnapshotVersions
        .toList
        .traverseU(snapshotVersion)
    } yield SnapshotVersioning(
      Module(organization, name, Map.empty),
      version,
      latest,
      release,
      timestamp,
      buildNumber,
      localCopy,
      lastUpdatedOpt,
      if (!snapshotVersions.isEmpty)
        snapshotVersions
      else
        buildNumber.map(bn => guessedSnapshotVersion(version, timestamp, bn)).toList
    )
  }

  val extraAttributeSeparator = ":#@#:"
  val extraAttributePrefix = "+"

  val extraAttributeOrg = "organisation"
  val extraAttributeName = "module"
  val extraAttributeVersion = "revision"

  val extraAttributeBase = Set(
    extraAttributeOrg,
    extraAttributeName,
    extraAttributeVersion,
    "branch"
  )

  val extraAttributeDropPrefix = "e:"

  def extraAttribute(s: String): String \/ (Module, String) = {
    // vaguely does the same as:
    // https://github.com/apache/ant-ivy/blob/2.2.0/src/java/org/apache/ivy/core/module/id/ModuleRevisionId.java#L291

    // dropping the attributes with a value of NULL here...

    val rawParts = s.split(extraAttributeSeparator).toSeq

    val partsOrError =
      if (rawParts.length % 2 == 0) {
        val malformed = rawParts.filter(!_.startsWith(extraAttributePrefix))
        if (malformed.isEmpty)
          \/-(rawParts.map(_.drop(extraAttributePrefix.length)))
        else
          -\/(s"Malformed attributes ${malformed.map("'"+_+"'").mkString(", ")} in extra attributes '$s'")
      } else
        -\/(s"Malformed extra attributes '$s'")

    def attrFrom(attrs: Map[String, String], name: String): String \/ String =
      \/.fromEither(
        attrs.get(name)
          .toRight(s"$name not found in extra attributes '$s'")
      )

    for {
      parts <- partsOrError
      attrs = parts.grouped(2).collect {
        case Seq(k, v) if v != "NULL" =>
          k.stripPrefix(extraAttributeDropPrefix) -> v
      }.toMap
      org <- attrFrom(attrs, extraAttributeOrg)
      name <- attrFrom(attrs, extraAttributeName)
      version <- attrFrom(attrs, extraAttributeVersion)
      remainingAttrs = attrs.filterKeys(!extraAttributeBase(_))
    } yield (Module(org, name, remainingAttrs.toVector.toMap), version)
  }

  def extraAttributes(s: String): String \/ Seq[(Module, String)] = {
    val lines = s.split('\n').toSeq.map(_.trim).filter(_.nonEmpty)

    lines.foldLeft[String \/ Seq[(Module, String)]](\/-(Vector.empty)) {
      case (acc, line) =>
        for {
          modVers <- acc
          modVer <- extraAttribute(line)
        } yield modVers :+ modVer
    }
  }

  def addOptionalDependenciesInConfig(
    proj: Project,
    fromConfigs: Set[String],
    optionalConfig: String
  ): Project = {

    val optionalDeps = proj.dependencies.collect {
      case (conf, dep) if dep.optional && fromConfigs(conf) =>
        optionalConfig -> dep.copy(optional = false)
    }

    val configurations = proj.configurations +
      (optionalConfig -> (proj.configurations.getOrElse(optionalConfig, Nil) ++ fromConfigs.filter(_.nonEmpty)).distinct)

    proj.copy(
      configurations = configurations,
      dependencies = proj.dependencies ++ optionalDeps
    )
  }
}
