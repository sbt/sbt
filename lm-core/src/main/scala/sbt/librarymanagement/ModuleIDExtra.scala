/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import java.net.URI

import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import scala.collection.mutable.ListBuffer
import sbt.librarymanagement.syntax._
import sbt.util.Logger

private[librarymanagement] abstract class ModuleIDExtra {
  def organization: String
  def name: String
  def revision: String
  def configurations: Option[String]
  def isChanging: Boolean
  def isTransitive: Boolean
  def isForce: Boolean
  def explicitArtifacts: Vector[Artifact]
  def inclusions: Vector[InclusionRule]
  def exclusions: Vector[ExclusionRule]
  def extraAttributes: Map[String, String]
  def crossVersion: CrossVersion
  def branchName: Option[String]

  def withIsChanging(isChanging: Boolean): ModuleID
  def withIsTransitive(isTransitive: Boolean): ModuleID
  def withIsForce(isForce: Boolean): ModuleID
  def withExplicitArtifacts(explicitArtifacts: Vector[Artifact]): ModuleID
  def withExclusions(exclusions: Vector[InclExclRule]): ModuleID
  def withExtraAttributes(extraAttributes: Map[String, String]): ModuleID
  def withCrossVersion(crossVersion: CrossVersion): ModuleID
  def withBranchName(branchName: Option[String]): ModuleID
  def withPlatformOpt(platformOpt: Option[String]): ModuleID

  protected def toStringImpl: String =
    s"""$organization:$name:$revision""" +
      (configurations match { case Some(s) => ":" + s; case None => "" }) + {
        val attr = attributeString
        if (attr == "") ""
        else " " + attr
      } +
      (if (extraAttributes.isEmpty) "" else " " + extraString)

  protected def attributeString: String = {
    val buffer = ListBuffer.empty[String]
    if (isChanging) {
      buffer += "changing"
    }
    if (!isTransitive) {
      buffer += "intransitive"
    }
    if (isForce) {
      buffer += "force"
    }
    buffer.toList.mkString(";")
  }

  /** String representation of the extra attributes, excluding any information only attributes. */
  def extraString: String =
    extraDependencyAttributes.map { case (k, v) => k + "=" + v }.mkString("(", ", ", ")")

  /** Returns the extra attributes except for ones marked as information only (ones that typically would not be used for dependency resolution). */
  def extraDependencyAttributes: Map[String, String] =
    extraAttributes.view.filterKeys(!_.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX)).toMap

  @deprecated(
    "Use `cross(CrossVersion)`, the variant accepting a CrossVersion value constructed by a member of the CrossVersion object instead.",
    "0.12.0"
  )
  def cross(v: Boolean): ModuleID = cross(if (v) CrossVersion.binary else Disabled())

  /**
   * Specifies the cross-version behavior for this module. See [CrossVersion] for details.
   * Unlike `withCrossVersion(...)`, `cross(...)` will preserve the prefix and suffix
   * values from the existing `crossVersion` value.
   *
   * {{{
   * ModuleID("com.example", "foo", "1.0")
   *     .cross(CrossVersion.binaryWith("sjs1_", ""))
   *     .cross(CrossVersion.for3Use2_13)
   * }}}
   *
   * This allows `.cross(...)` to play well with `%%%` operator provided by sbt-platform-deps.
   */
  def cross(v: CrossVersion): ModuleID =
    withCrossVersion(CrossVersion.getPrefixSuffix(this.crossVersion) match {
      case ("", "") => v
      case (prefix, suffix) =>
        CrossVersion.getPrefixSuffix(v) match {
          case ("", "") => CrossVersion.setPrefixSuffix(v, prefix, suffix)
          case _        => v
        }
    })

  // () required for chaining
  /** Do not follow dependencies of this module.  Synonym for `intransitive`. */
  def notTransitive(): ModuleID = intransitive()

  /** Do not follow dependencies of this module.  Synonym for `notTransitive`. */
  def intransitive(): ModuleID = withIsTransitive(false)

  /**
   * Marks this dependency as "changing".  Ivy will always check if the metadata has changed and then if the artifact has changed,
   * redownload it.  sbt configures all -SNAPSHOT dependencies to be changing.
   *
   * See the "Changes in artifacts" section of https://ant.apache.org/ivy/history/trunk/concept.html for full details.
   */
  def changing(): ModuleID = withIsChanging(true)

  /**
   * Indicates that conflict resolution should only select this module's revision.
   * This prevents a newer revision from being pulled in by a transitive dependency, for example.
   */
  def force(): ModuleID = withIsForce(true)

  private[sbt] def validateProtocol(logger: Logger): Unit = {
    explicitArtifacts foreach { _.validateProtocol(logger) }
  }

  /**
   * Specifies a URL from which the main artifact for this dependency can be downloaded.
   * This value is only consulted if the module is not found in a repository.
   * It is not included in published metadata.
   */
  def from(url: String): ModuleID = from(url, false)

  /**
   * Specifies a URL from which the main artifact for this dependency can be downloaded.
   * This value is only consulted if the module is not found in a repository.
   * It is not included in published metadata.
   */
  def from(url: String, allowInsecureProtocol: Boolean): ModuleID =
    artifacts(Artifact(name, new URI(url), allowInsecureProtocol))

  /** Adds a dependency on the artifact for this module with classifier `c`. */
  def classifier(c: String): ModuleID = artifacts(Artifact(name, c))

  /**
   * Declares the explicit artifacts for this module.  If this ModuleID represents a dependency,
   * these artifact definitions override the information in the dependency's published metadata.
   */
  def artifacts(newArtifacts: Artifact*): ModuleID =
    withExplicitArtifacts(newArtifacts.toVector ++ explicitArtifacts)

  /**
   * Applies the provided exclusions to dependencies of this module.  Note that only exclusions that specify
   * both the exact organization and name and nothing else will be included in a pom.xml.
   */
  def excludeAll(rules: ExclusionRule*): ModuleID = withExclusions(exclusions ++ rules)

  /** Excludes the dependency with organization `org` and `name` from being introduced by this dependency during resolution. */
  def exclude(org: String, name: String): ModuleID =
    excludeAll(ExclusionRule().withOrganization(org).withName(name))

  /**
   * Adds extra attributes for this module.  All keys are prefixed with `e:` if they are not already so prefixed.
   * This information will only be published in an ivy.xml and not in a pom.xml.
   */
  def extra(attributes: (String, String)*): ModuleID =
    withExtraAttributes(extraAttributes ++ ModuleID.checkE(attributes))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "sources"
   * classifier.  If you want to also depend on the main artifact, be sure to also call `jar()` or use `withSources()` instead.
   */
  def sources(): ModuleID = artifacts(Artifact.sources(name))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "javadoc"
   * classifier.  If you want to also depend on the main artifact, be sure to also call `jar()` or use `withJavadoc()` instead.
   */
  def javadoc(): ModuleID = artifacts(Artifact.javadoc(name))

  def pomOnly(): ModuleID = artifacts(Artifact.pom(name))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "sources"
   * classifier.  If there is not already an explicit dependency on the main artifact, this adds one.
   */
  def withSources(): ModuleID = jarIfEmpty.sources()

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "javadoc"
   * classifier.  If there is not already an explicit dependency on the main artifact, this adds one.
   */
  def withJavadoc(): ModuleID = jarIfEmpty.javadoc()

  private def jarIfEmpty = if (explicitArtifacts.isEmpty) jar() else this

  /**
   * Declares a dependency on the main artifact.  This is implied by default unless artifacts are explicitly declared, such
   * as when adding a dependency on an artifact with a classifier.
   */
  def jar(): ModuleID = artifacts(Artifact(name))

  /**
   * Sets the Ivy branch of this module.
   */
  def branch(branchName: String): ModuleID = withBranchName(Some(branchName))

  def branch(branchName: Option[String]): ModuleID = withBranchName(branchName)

  def platform(platform: String): ModuleID = withPlatformOpt(Some(platform))
}

private[librarymanagement] abstract class ModuleIDFunctions {

  /** Prefixes all keys with `e:` if they are not already so prefixed. */
  def checkE(attributes: Seq[(String, String)]) =
    for ((key, value) <- attributes)
      yield if (key.startsWith("e:")) (key, value) else ("e:" + key, value)
}
