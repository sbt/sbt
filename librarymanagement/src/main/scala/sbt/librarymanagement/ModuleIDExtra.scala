/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.librarymanagement

import java.net.URL

import sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties
import scala.collection.mutable.ListBuffer

abstract class ModuleIDExtra {
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
  def checksum: Option[Checksum]

  protected[this] def copy(
      organization: String = organization,
      name: String = name,
      revision: String = revision,
      configurations: Option[String] = configurations,
      isChanging: Boolean = isChanging,
      isTransitive: Boolean = isTransitive,
      isForce: Boolean = isForce,
      explicitArtifacts: Vector[Artifact] = explicitArtifacts,
      inclusions: Vector[InclusionRule] = inclusions,
      exclusions: Vector[ExclusionRule] = exclusions,
      extraAttributes: Map[String, String] = extraAttributes,
      crossVersion: CrossVersion = crossVersion,
      branchName: Option[String] = branchName,
      checksum: Option[Checksum] = checksum
  ): ModuleID

  protected def toStringImpl: String =
    s"""$organization:$name:$revision""" +
      (checksum match { case Some(s)       => s": ${s.`type`} ${s.digest}"; case None => "" }) +
      (configurations match { case Some(s) => ":" + s; case None                      => "" }) + {
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
    extraDependencyAttributes.map { case (k, v) => k + "=" + v } mkString ("(", ", ", ")")

  /** Returns the extra attributes except for ones marked as information only (ones that typically would not be used for dependency resolution). */
  def extraDependencyAttributes: Map[String, String] =
    extraAttributes.filterKeys(!_.startsWith(SbtPomExtraProperties.POM_INFO_KEY_PREFIX))

  @deprecated(
    "Use `cross(CrossVersion)`, the variant accepting a CrossVersion value constructed by a member of the CrossVersion object instead.",
    "0.12.0"
  )
  def cross(v: Boolean): ModuleID = cross(if (v) CrossVersion.binary else Disabled())

  /** Specifies the cross-version behavior for this module.  See [CrossVersion] for details.*/
  def cross(v: CrossVersion): ModuleID = copy(crossVersion = v)

  // () required for chaining
  /** Do not follow dependencies of this module.  Synonym for `intransitive`.*/
  def notTransitive() = intransitive()

  /** Do not follow dependencies of this module.  Synonym for `notTransitive`.*/
  def intransitive() = copy(isTransitive = false)

  /**
   * Marks this dependency as "changing".  Ivy will always check if the metadata has changed and then if the artifact has changed,
   * redownload it.  sbt configures all -SNAPSHOT dependencies to be changing.
   *
   * See the "Changes in artifacts" section of https://ant.apache.org/ivy/history/trunk/concept.html for full details.
   */
  def changing() = copy(isChanging = true)

  /**
   * Indicates that conflict resolution should only select this module's revision.
   * This prevents a newer revision from being pulled in by a transitive dependency, for example.
   */
  def force() = copy(isForce = true)

  /**
   * Specifies a URL from which the main artifact for this dependency can be downloaded.
   * This value is only consulted if the module is not found in a repository.
   * It is not included in published metadata.
   */
  def from(url: String) = artifacts(Artifact(name, new URL(url)))

  /** Adds a dependency on the artifact for this module with classifier `c`. */
  def classifier(c: String) = artifacts(Artifact(name, c))

  /**
   * Declares the explicit artifacts for this module.  If this ModuleID represents a dependency,
   * these artifact definitions override the information in the dependency's published metadata.
   */
  def artifacts(newArtifacts: Artifact*) =
    copy(explicitArtifacts = newArtifacts.toVector ++ explicitArtifacts)

  /**
   * Applies the provided exclusions to dependencies of this module.  Note that only exclusions that specify
   * both the exact organization and name and nothing else will be included in a pom.xml.
   */
  def excludeAll(rules: ExclusionRule*) = copy(exclusions = this.exclusions ++ rules)

  /** Excludes the dependency with organization `org` and `name` from being introduced by this dependency during resolution. */
  def exclude(org: String, name: String) =
    excludeAll(ExclusionRule().withOrganization(org).withName(name))

  /**
   * Adds extra attributes for this module.  All keys are prefixed with `e:` if they are not already so prefixed.
   * This information will only be published in an ivy.xml and not in a pom.xml.
   */
  def extra(attributes: (String, String)*) =
    copy(extraAttributes = this.extraAttributes ++ ModuleID.checkE(attributes))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "sources"
   * classifier.  If you want to also depend on the main artifact, be sure to also call `jar()` or use `withSources()` instead.
   */
  def sources() = artifacts(Artifact.sources(name))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "javadoc"
   * classifier.  If you want to also depend on the main artifact, be sure to also call `jar()` or use `withJavadoc()` instead.
   */
  def javadoc() = artifacts(Artifact.javadoc(name))

  def pomOnly() = artifacts(Artifact.pom(name))

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "sources"
   * classifier.  If there is not already an explicit dependency on the main artifact, this adds one.
   */
  def withSources() = jarIfEmpty.sources()

  /**
   * Not recommended for new use.  This method is not deprecated, but the `update-classifiers` task is preferred
   * for performance and correctness.  This method adds a dependency on this module's artifact with the "javadoc"
   * classifier.  If there is not already an explicit dependency on the main artifact, this adds one.
   */
  def withJavadoc() = jarIfEmpty.javadoc()

  private def jarIfEmpty = if (explicitArtifacts.isEmpty) jar() else this

  /**
   * Declares a dependency on the main artifact.  This is implied by default unless artifacts are explicitly declared, such
   * as when adding a dependency on an artifact with a classifier.
   */
  def jar() = artifacts(Artifact(name))

  /**
   * Sets the Ivy branch of this module.
   */
  def branch(branchName: String) = copy(branchName = Some(branchName))

  def branch(branchName: Option[String]) = copy(branchName = branchName)
}

abstract class ModuleIDFunctions {

  /** Prefixes all keys with `e:` if they are not already so prefixed. */
  def checkE(attributes: Seq[(String, String)]) =
    for ((key, value) <- attributes)
      yield if (key.startsWith("e:")) (key, value) else ("e:" + key, value)
}
