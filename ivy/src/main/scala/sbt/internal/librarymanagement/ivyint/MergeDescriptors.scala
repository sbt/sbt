package sbt.internal.librarymanagement
package ivyint

import scala.collection.immutable.ArraySeq
import org.apache.ivy.core
import core.module.descriptor.{ DependencyArtifactDescriptor, DefaultDependencyArtifactDescriptor }
import core.module.descriptor.DependencyDescriptor
import core.module.id.{ ArtifactId, ModuleRevisionId }

private[sbt] object MergeDescriptors {
  def mergeable(a: DependencyDescriptor, b: DependencyDescriptor): Boolean =
    a.isForce == b.isForce &&
      a.isChanging == b.isChanging &&
      a.isTransitive == b.isTransitive &&
      a.getParentRevisionId == b.getParentRevisionId &&
      a.getNamespace == b.getNamespace && {
        val amrid = a.getDependencyRevisionId
        val bmrid = b.getDependencyRevisionId
        amrid == bmrid
      } && {
        val adyn = a.getDynamicConstraintDependencyRevisionId
        val bdyn = b.getDynamicConstraintDependencyRevisionId
        adyn == bdyn
      }

  def apply(a: DependencyDescriptor, b: DependencyDescriptor): DependencyDescriptor = {
    assert(mergeable(a, b))
    new MergedDescriptors(a, b)
  }
}

// combines the artifacts, configurations, includes, and excludes for DependencyDescriptors `a` and `b`
// that otherwise have equal IDs
private[sbt] final case class MergedDescriptors(a: DependencyDescriptor, b: DependencyDescriptor)
    extends DependencyDescriptor {
  def getDependencyId = a.getDependencyId
  def isForce = a.isForce
  def isChanging = a.isChanging
  def isTransitive = a.isTransitive
  def getNamespace = a.getNamespace
  def getParentRevisionId = a.getParentRevisionId
  def getDependencyRevisionId = a.getDependencyRevisionId
  def getDynamicConstraintDependencyRevisionId = a.getDynamicConstraintDependencyRevisionId

  def getModuleConfigurations = concat(a.getModuleConfigurations, b.getModuleConfigurations)

  def getDependencyConfigurations(moduleConfiguration: String, requestedConfiguration: String) =
    concat(
      a.getDependencyConfigurations(moduleConfiguration, requestedConfiguration),
      b.getDependencyConfigurations(moduleConfiguration)
    )

  def getDependencyConfigurations(moduleConfiguration: String) =
    concat(
      a.getDependencyConfigurations(moduleConfiguration),
      b.getDependencyConfigurations(moduleConfiguration)
    )

  def getDependencyConfigurations(moduleConfigurations: Array[String]) =
    concat(
      a.getDependencyConfigurations(moduleConfigurations),
      b.getDependencyConfigurations(moduleConfigurations)
    )

  def getAllDependencyArtifacts =
    concatArtifacts(a, a.getAllDependencyArtifacts, b, b.getAllDependencyArtifacts)

  def getDependencyArtifacts(moduleConfigurations: String) =
    concatArtifacts(
      a,
      a.getDependencyArtifacts(moduleConfigurations),
      b,
      b.getDependencyArtifacts(moduleConfigurations)
    )

  def getDependencyArtifacts(moduleConfigurations: Array[String]) =
    concatArtifacts(
      a,
      a.getDependencyArtifacts(moduleConfigurations),
      b,
      b.getDependencyArtifacts(moduleConfigurations)
    )

  def getAllIncludeRules = concat(a.getAllIncludeRules, b.getAllIncludeRules)

  def getIncludeRules(moduleConfigurations: String) =
    concat(a.getIncludeRules(moduleConfigurations), b.getIncludeRules(moduleConfigurations))

  def getIncludeRules(moduleConfigurations: Array[String]) =
    concat(a.getIncludeRules(moduleConfigurations), b.getIncludeRules(moduleConfigurations))

  private[this] def concatArtifacts(
      a: DependencyDescriptor,
      as: Array[DependencyArtifactDescriptor],
      b: DependencyDescriptor,
      bs: Array[DependencyArtifactDescriptor]
  ) = {
    if (as.isEmpty)
      if (bs.isEmpty) as
      else defaultArtifact(a) ++ explicitConfigurations(b, bs)
    else if (bs.isEmpty) explicitConfigurations(a, as) ++ defaultArtifact(b)
    else concat(explicitConfigurations(a, as), explicitConfigurations(b, bs))
  }
  private[this] def explicitConfigurations(
      base: DependencyDescriptor,
      arts: Array[DependencyArtifactDescriptor]
  ): Array[DependencyArtifactDescriptor] =
    arts map { art =>
      explicitConfigurations(base, art)
    }
  private[this] def explicitConfigurations(
      base: DependencyDescriptor,
      art: DependencyArtifactDescriptor
  ): DependencyArtifactDescriptor = {
    val aConfs = Option(art.getConfigurations) map { _.toList }
    // In case configuration list is "*", we should still specify the module configuration of the DependencyDescriptor
    // otherwise the explicit specified artifacts from one dd can leak over to the other.
    // See gh-1500, gh-2002
    aConfs match {
      case None | Some(Nil) | Some(List("*")) =>
        copyWithConfigurations(art, ArraySeq.unsafeWrapArray(base.getModuleConfigurations))
      case _ => art
    }
  }
  private[this] def defaultArtifact(
      a: DependencyDescriptor
  ): Array[DependencyArtifactDescriptor] = {
    val dd = new DefaultDependencyArtifactDescriptor(
      a,
      a.getDependencyRevisionId.getName,
      "jar",
      "jar",
      null,
      null
    )
    addConfigurations(dd, ArraySeq.unsafeWrapArray(a.getModuleConfigurations))
    // If the dependency descriptor is empty, then it means that it has been created from a POM file. In this case,
    // it is correct to create a seemingly non-existent dependency artifact.
    if (a.getAllDependencyArtifacts.isEmpty) Array(dd)
    else a.getAllDependencyArtifacts filter (_ == dd)
  }
  private[this] def copyWithConfigurations(
      dd: DependencyArtifactDescriptor,
      confs: Seq[String]
  ): DependencyArtifactDescriptor = {
    val dextra = dd.getQualifiedExtraAttributes
    val newd = new DefaultDependencyArtifactDescriptor(
      dd.getDependencyDescriptor,
      dd.getName,
      dd.getType,
      dd.getExt,
      dd.getUrl,
      dextra
    )
    addConfigurations(newd, confs)
    newd
  }
  private[this] def addConfigurations(
      dd: DefaultDependencyArtifactDescriptor,
      confs: Seq[String]
  ): Unit =
    confs foreach dd.addConfiguration

  private[this] def concat[T: reflect.ClassTag](a: Array[T], b: Array[T]): Array[T] =
    (a ++ b).distinct

  def getAllExcludeRules = concat(a.getAllExcludeRules, b.getAllExcludeRules)

  def getExcludeRules(moduleConfigurations: String) =
    concat(a.getExcludeRules(moduleConfigurations), b.getExcludeRules(moduleConfigurations))

  def getExcludeRules(moduleConfigurations: Array[String]) =
    concat(a.getExcludeRules(moduleConfigurations), b.getExcludeRules(moduleConfigurations))

  def doesExclude(moduleConfigurations: Array[String], artifactId: ArtifactId) =
    a.doesExclude(moduleConfigurations, artifactId) || b.doesExclude(
      moduleConfigurations,
      artifactId
    )

  def canExclude = a.canExclude || b.canExclude

  def asSystem = this

  def clone(revision: ModuleRevisionId) =
    new MergedDescriptors(a.clone(revision), b.clone(revision))

  def getAttribute(name: String): String = a.getAttribute(name)
  def getAttributes = a.getAttributes
  def getExtraAttribute(name: String) = a.getExtraAttribute(name)
  def getExtraAttributes = a.getExtraAttributes
  def getQualifiedExtraAttributes = a.getQualifiedExtraAttributes
  def getSourceModule = a.getSourceModule
}
