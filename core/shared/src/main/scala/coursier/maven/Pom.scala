package coursier.maven

import coursier.core._
import scalaz.Scalaz.{eitherMonad, listInstance, ToTraverseOps}

object Pom {
  import coursier.util.Xml._

  private def point[T](t: T) =
    Right(t).right

  /**
    * Returns either a property's key-value pair or an error if the elem is not an element.
    *
    * This method trims all spaces, whereas Maven has an option to preserve them.
    *
    * @param elem a property element
    * @return the key and the value of the property
    * @see [[https://issues.apache.org/jira/browse/MNG-5380]]
    */
  def property(elem: Node): Either[String, (String, String)] = {
    // Not matching with Text, which fails on scala-js if the property value has xml comments
    if (elem.isElement) Right(elem.label -> elem.textContent.trim)
    else Left(s"Can't parse property $elem")
  }

  // TODO Allow no version in some contexts
  private def module(
    node: Node,
    defaultGroupId: Option[String] = None,
    defaultArtifactId: Option[String] = None
  ): Either[String, Module] = {
    for {
      organization <- {
        val e = text(node, "groupId", "Organization")
        defaultGroupId.fold(e)(g => Right(e.right.getOrElse(g))).right
      }
      name <- {
        val n = text(node, "artifactId", "Name")
        defaultArtifactId.fold(n)(n0 => Right(n.right.getOrElse(n0))).right
      }
    } yield Module(organization, name, Map.empty).trim
  }

  private def readVersion(node: Node) =
    text(node, "version", "Version").right.getOrElse("").trim

  def dependency(node: Node): Either[String, (String, Dependency)] =
    module(node).right.flatMap { mod =>

      val version0 = readVersion(node)
      val scopeOpt = text(node, "scope", "").right.toOption
      val typeOpt = text(node, "type", "").right.toOption
      val classifierOpt = text(node, "classifier", "").right.toOption
      val xmlExclusions = node.children
        .find(_.label == "exclusions")
        .map(_.children.filter(_.label == "exclusion"))
        .getOrElse(Seq.empty)

      xmlExclusions.toList.traverseU(module(_, defaultArtifactId = Some("*"))).right.map { exclusions =>

        val optional = text(node, "optional", "").right.toSeq.contains("true")

        scopeOpt.getOrElse("") -> Dependency(
          mod,
          version0,
          "",
          exclusions.map(mod => (mod.organization, mod.name)).toSet,
          Attributes(typeOpt.getOrElse(""), classifierOpt.getOrElse("")),
          optional,
          transitive = true
        )
      }
    }

  private def profileActivation(node: Node): (Option[Boolean], Activation) = {
    val byDefault =
      text(node, "activeByDefault", "").right.toOption.flatMap {
        case "true" => Some(true)
        case "false" => Some(false)
        case _ => None
      }

    val properties = node.children
      .filter(_.label == "property")
      .flatMap { p =>
        for{
          name <- text(p, "name", "").right.toOption
          valueOpt = text(p, "value", "").right.toOption
        } yield (name, valueOpt)
      }

    val osNodeOpt = node.children.collectFirst { case n if n.label == "os" => n }

    val os = Activation.Os(
      osNodeOpt.flatMap(n => text(n, "arch", "").right.toOption),
      osNodeOpt.flatMap(n => text(n, "family", "").right.toOption).toSet,
      osNodeOpt.flatMap(n => text(n, "name", "").right.toOption),
      osNodeOpt.flatMap(n => text(n, "version", "").right.toOption)
    )

    val jdk = text(node, "jdk", "").right.toOption.flatMap { s =>
      Parse.versionInterval(s)
        .orElse(Parse.multiVersionInterval(s))
        .map(Left(_))
        .orElse(Parse.version(s).map(v => Right(Seq(v))))
    }

    val activation = Activation(properties, os, jdk)

    (byDefault, activation)
  }

  def profile(node: Node): Either[String, Profile] = {

    val id = text(node, "id", "Profile ID").right.getOrElse("")

    val xmlActivationOpt = node.children
      .find(_.label == "activation")
    val (activeByDefault, activation) = xmlActivationOpt.fold((Option.empty[Boolean], Activation.empty))(profileActivation)

    val xmlDeps = node.children
      .find(_.label == "dependencies")
      .map(_.children.filter(_.label == "dependency"))
      .getOrElse(Seq.empty)

    for {
      deps <- xmlDeps.toList.traverseU(dependency).right

      depMgmts <- node
        .children
        .find(_.label == "dependencyManagement")
        .flatMap(_.children.find(_.label == "dependencies"))
        .map(_.children.filter(_.label == "dependency"))
        .getOrElse(Seq.empty)
        .toList
        .traverseU(dependency)
        .right

      properties <- node
        .children
        .find(_.label == "properties")
        .map(_.children.collect { case elem if elem.isElement => elem })
        .getOrElse(Seq.empty)
        .toList
        .traverseU(property)
        .right

    } yield Profile(id, activeByDefault, activation, deps, depMgmts, properties.toMap)
  }

  def packagingOpt(pom: Node): Option[String] =
    text(pom, "packaging", "").right.toOption

  def project(pom: Node): Either[String, Project] =
    project(pom, relocationAsDependency = false)

  def project(
    pom: Node,
    relocationAsDependency: Boolean
  ): Either[String, Project] = {

    for {
      projModule <- module(pom, defaultGroupId = Some("")).right

      parentOpt <- point(pom.children.find(_.label == "parent"))
      parentModuleOpt <- parentOpt
        .map(module(_).right.map(Some(_)))
        .getOrElse(Right(None))
        .right
      parentVersionOpt <- point(parentOpt.map(readVersion))

      xmlDeps <- point(
        pom.children
          .find(_.label == "dependencies")
          .map(_.children.filter(_.label == "dependency"))
          .getOrElse(Seq.empty)
      )
      deps <- xmlDeps.toList.traverseU(dependency).right

      xmlDepMgmts <- point(
        pom.children
          .find(_.label == "dependencyManagement")
          .flatMap(_.children.find(_.label == "dependencies"))
          .map(_.children.filter(_.label == "dependency"))
          .getOrElse(Seq.empty)
      )
      depMgmts <- xmlDepMgmts.toList.traverseU(dependency).right

      groupId <- Some(projModule.organization).filter(_.nonEmpty)
        .orElse(parentModuleOpt.map(_.organization).filter(_.nonEmpty))
        .toRight("No organization found")
        .right
      version <- Some(readVersion(pom)).filter(_.nonEmpty)
        .orElse(parentVersionOpt.filter(_.nonEmpty))
        .toRight("No version found")
        .right

      _ <- parentVersionOpt
        .map(v => if (v.isEmpty) Left("Parent version missing") else Right(()))
        .getOrElse(Right(()))
        .right
      _ <- parentModuleOpt
        .map(mod => if (mod.organization.isEmpty) Left("Parent organization missing") else Right(()))
        .getOrElse(Right(()))
        .right

      xmlProperties <- point(
        pom.children
          .find(_.label == "properties")
          .map(_.children.collect{case elem if elem.isElement => elem})
          .getOrElse(Seq.empty)
      )
      properties <- xmlProperties.toList.traverseU(property).right

      xmlProfiles <- point(
        pom
          .children
          .find(_.label == "profiles")
          .map(_.children.filter(_.label == "profile"))
          .getOrElse(Seq.empty)
      )
      profiles <- xmlProfiles.toList.traverseU(profile).right

      extraAttrs <- properties
        .collectFirst { case ("extraDependencyAttributes", s) => extraAttributes(s) }
        .getOrElse(Right(Map.empty))
        .right

    } yield {

      val extraAttrsMap = extraAttrs
        .map {
          case (mod, ver) =>
            (mod.copy(attributes = Map.empty), ver) -> mod.attributes
        }
        .toMap

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
          text(n, "name", "License name").right.toOption.map { name =>
            (name, text(n, "url", "License URL").right.toOption)
          }.toSeq
        }

      val developers = pom.children
        .find(_.label == "developers")
        .toSeq
        .flatMap(_.children)
        .filter(_.label == "developer")
        .map { n =>
          for {
            id <- text(n, "id", "Developer ID").right
            name <- text(n, "name", "Developer name").right
            url <- text(n, "url", "Developer URL").right
          } yield Info.Developer(id, name, url)
        }
        .collect {
          case Right(d) => d
        }

      val finalProjModule = projModule.copy(organization = groupId)

      val relocationDependencyOpt =
        if (relocationAsDependency)
          pom.children
            .find(_.label == "distributionManagement")
            .flatMap(_.children.find(_.label == "relocation"))
            .map { n =>

              // see https://maven.apache.org/guides/mini/guide-relocation.html

              val relocatedGroupId = text(n, "groupId", "").right.getOrElse(finalProjModule.organization)
              val relocatedArtifactId = text(n, "artifactId", "").right.getOrElse(finalProjModule.name)
              val relocatedVersion = text(n, "version", "").right.getOrElse(version)

              "" -> Dependency(
                finalProjModule.copy(
                  organization = relocatedGroupId,
                  name = relocatedArtifactId
                ),
                relocatedVersion,
                "",
                Set(),
                Attributes("", ""),
                optional = false,
                transitive = true
              )
            }
        else
          None

      Project(
        finalProjModule,
        version,
        (relocationDependencyOpt.toList ::: deps).map {
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
        relocationDependencyOpt.fold(packagingOpt(pom))(_ => Some(relocatedPackaging)),
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

  def versions(node: Node): Either[String, Versions] = {

    for {
      organization <- text(node, "groupId", "Organization").right // Ignored
      name <- text(node, "artifactId", "Name").right // Ignored

      xmlVersioning <- node.children
        .find(_.label == "versioning")
        .toRight("Versioning info not found in metadata")
        .right

    } yield {

      val latest = text(xmlVersioning, "latest", "Latest version")
        .right
        .getOrElse("")
      val release = text(xmlVersioning, "release", "Release version")
        .right
        .getOrElse("")

      val versionsOpt = xmlVersioning.children
        .find(_.label == "versions")
        .map(_.children.filter(_.label == "version").flatMap(_.children.collectFirst { case Text(t) => t }))

      val lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .right
        .toOption
        .flatMap(parseDateTime)

      Versions(latest, release, versionsOpt.map(_.toList).getOrElse(Nil), lastUpdatedOpt)
    }
  }

  def snapshotVersion(node: Node): Either[String, SnapshotVersion] = {

    def textOrEmpty(name: String, desc: String): String =
      text(node, name, desc)
        .right
        .getOrElse("")

    val classifier = textOrEmpty("classifier", "Classifier")
    val ext = textOrEmpty("extension", "Extensions")
    val value = textOrEmpty("value", "Value")

    val updatedOpt = text(node, "updated", "Updated")
      .right
      .toOption
      .flatMap(parseDateTime)

    Right(SnapshotVersion(
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

  def snapshotVersioning(node: Node): Either[String, SnapshotVersioning] =
    // FIXME Quite similar to Versions above
    for {
      organization <- text(node, "groupId", "Organization").right
      name <- text(node, "artifactId", "Name").right

      xmlVersioning <- node
        .children
        .find(_.label == "versioning")
        .toRight("Versioning info not found in metadata")
        .right

      snapshotVersions <- {

        val xmlSnapshotVersions = xmlVersioning
          .children
          .find(_.label == "snapshotVersions")
          .map(_.children.filter(_.label == "snapshotVersion"))
          .getOrElse(Seq.empty)

        xmlSnapshotVersions
          .toList
          .traverseU(snapshotVersion)
          .right
      }
    } yield {

      val version = readVersion(node)

      val latest = text(xmlVersioning, "latest", "Latest version")
        .right
        .getOrElse("")
      val release = text(xmlVersioning, "release", "Release version")
        .right
        .getOrElse("")

      val lastUpdatedOpt = text(xmlVersioning, "lastUpdated", "Last update date and time")
        .right
        .toOption
        .flatMap(parseDateTime)

      val xmlSnapshotOpt = xmlVersioning
        .children
        .find(_.label == "snapshot")

      val timestamp = xmlSnapshotOpt
        .flatMap(
          text(_, "timestamp", "Snapshot timestamp")
            .right
            .toOption
        )
        .getOrElse("")

      val buildNumber = xmlSnapshotOpt
        .flatMap(
          text(_, "buildNumber", "Snapshot build number")
            .right
            .toOption
        )
        .filter(s => s.nonEmpty && s.forall(_.isDigit))
        .map(_.toInt)

      val localCopy = xmlSnapshotOpt
        .flatMap(
          text(_, "localCopy", "Snapshot local copy")
            .right
            .toOption
        )
        .collect {
          case "true" => true
          case "false" => false
        }

      SnapshotVersioning(
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

  val relocatedPackaging = s"$$relocated"

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

  def extraAttribute(s: String): Either[String, (Module, String)] = {
    // vaguely does the same as:
    // https://github.com/apache/ant-ivy/blob/2.2.0/src/java/org/apache/ivy/core/module/id/ModuleRevisionId.java#L291

    // dropping the attributes with a value of NULL here...

    val rawParts = s.split(extraAttributeSeparator).toSeq

    val partsOrError =
      if (rawParts.length % 2 == 0) {
        val malformed = rawParts.filter(!_.startsWith(extraAttributePrefix))
        if (malformed.isEmpty)
          Right(rawParts.map(_.drop(extraAttributePrefix.length)))
        else
          Left(s"Malformed attributes ${malformed.map("'"+_+"'").mkString(", ")} in extra attributes '$s'")
      } else
        Left(s"Malformed extra attributes '$s'")

    def attrFrom(attrs: Map[String, String], name: String): Either[String, String] =
      attrs
        .get(name)
        .toRight(s"$name not found in extra attributes '$s'")

    for {
      parts <- partsOrError.right
      attrs <- point(
        parts
          .grouped(2)
          .collect {
            case Seq(k, v) if v != "NULL" =>
              k.stripPrefix(extraAttributeDropPrefix) -> v
          }
          .toMap
      )
      org <- attrFrom(attrs, extraAttributeOrg).right
      name <- attrFrom(attrs, extraAttributeName).right
      version <- attrFrom(attrs, extraAttributeVersion).right
    } yield {
      val remainingAttrs = attrs.filterKeys(!extraAttributeBase(_))
      (Module(org, name, remainingAttrs.toVector.toMap), version)
    }
  }

  def extraAttributes(s: String): Either[String, Seq[(Module, String)]] = {

    val lines = s.split('\n').toSeq.map(_.trim).filter(_.nonEmpty)

    lines.foldLeft[Either[String, Seq[(Module, String)]]](Right(Vector.empty)) {
      case (acc, line) =>
        for {
          modVers <- acc.right
          modVer <- extraAttribute(line).right
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
