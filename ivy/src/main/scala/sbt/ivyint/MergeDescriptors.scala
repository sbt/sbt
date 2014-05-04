package sbt
package ivyint

import java.io.File
import java.net.URI
import java.util.{ Collection, Collections => CS }
import CS.singleton

import org.apache.ivy.{ core, plugins, util, Ivy }
import core.module.descriptor.{ DependencyArtifactDescriptor, DefaultDependencyArtifactDescriptor }
import core.module.descriptor.{ DefaultDependencyDescriptor => DDD, DependencyDescriptor }
import core.module.id.{ ArtifactId, ModuleId, ModuleRevisionId }
import plugins.namespace.Namespace
import util.extendable.ExtendableItem

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

  def apply(a: DependencyDescriptor, b: DependencyDescriptor): DependencyDescriptor =
    {
      assert(mergeable(a, b))
      new MergedDescriptors(a, b)
    }
}

// combines the artifacts, configurations, includes, and excludes for DependencyDescriptors `a` and `b`
// that otherwise have equal IDs
private final class MergedDescriptors(a: DependencyDescriptor, b: DependencyDescriptor) extends DependencyDescriptor {
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
    concat(a.getDependencyConfigurations(moduleConfiguration, requestedConfiguration), b.getDependencyConfigurations(moduleConfiguration))

  def getDependencyConfigurations(moduleConfiguration: String) =
    concat(a.getDependencyConfigurations(moduleConfiguration), b.getDependencyConfigurations(moduleConfiguration))

  def getDependencyConfigurations(moduleConfigurations: Array[String]) =
    concat(a.getDependencyConfigurations(moduleConfigurations), b.getDependencyConfigurations(moduleConfigurations))

  def getAllDependencyArtifacts = concatArtifacts(a, a.getAllDependencyArtifacts, b, b.getAllDependencyArtifacts)

  def getDependencyArtifacts(moduleConfigurations: String) =
    concatArtifacts(a, a.getDependencyArtifacts(moduleConfigurations), b, b.getDependencyArtifacts(moduleConfigurations))

  def getDependencyArtifacts(moduleConfigurations: Array[String]) =
    concatArtifacts(a, a.getDependencyArtifacts(moduleConfigurations), b, b.getDependencyArtifacts(moduleConfigurations))

  def getAllIncludeRules = concat(a.getAllIncludeRules, b.getAllIncludeRules)

  def getIncludeRules(moduleConfigurations: String) =
    concat(a.getIncludeRules(moduleConfigurations), b.getIncludeRules(moduleConfigurations))

  def getIncludeRules(moduleConfigurations: Array[String]) =
    concat(a.getIncludeRules(moduleConfigurations), b.getIncludeRules(moduleConfigurations))

  private[this] def concatArtifacts(a: DependencyDescriptor, as: Array[DependencyArtifactDescriptor], b: DependencyDescriptor, bs: Array[DependencyArtifactDescriptor]) =
    {
      if (as.isEmpty)
        if (bs.isEmpty) as
        else defaultArtifact(a) +: explicitConfigurations(b, bs)
      else if (bs.isEmpty) explicitConfigurations(a, as) :+ defaultArtifact(b)
      else concat(explicitConfigurations(a, as), explicitConfigurations(b, bs))
    }
  private[this] def explicitConfigurations(base: DependencyDescriptor, arts: Array[DependencyArtifactDescriptor]): Array[DependencyArtifactDescriptor] =
    arts map { art => explicitConfigurations(base, art) }
  private[this] def explicitConfigurations(base: DependencyDescriptor, art: DependencyArtifactDescriptor): DependencyArtifactDescriptor =
    {
      val aConfs = art.getConfigurations
      if (aConfs == null || aConfs.isEmpty)
        copyWithConfigurations(art, base.getModuleConfigurations)
      else
        art
    }
  private[this] def defaultArtifact(a: DependencyDescriptor): DependencyArtifactDescriptor =
    {
      val dd = new DefaultDependencyArtifactDescriptor(a, a.getDependencyRevisionId.getName, "jar", "jar", null, null)
      addConfigurations(dd, a.getModuleConfigurations)
      dd
    }
  private[this] def copyWithConfigurations(dd: DependencyArtifactDescriptor, confs: Seq[String]): DependencyArtifactDescriptor =
    {
      val dextra = dd.getQualifiedExtraAttributes
      val newd = new DefaultDependencyArtifactDescriptor(dd.getDependencyDescriptor, dd.getName, dd.getType, dd.getExt, dd.getUrl, dextra)
      addConfigurations(newd, confs)
      newd
    }
  private[this] def addConfigurations(dd: DefaultDependencyArtifactDescriptor, confs: Seq[String]): Unit =
    confs foreach dd.addConfiguration

  private[this] def concat[T: ClassManifest](a: Array[T], b: Array[T]): Array[T] = (a ++ b).distinct.toArray

  def getAllExcludeRules = concat(a.getAllExcludeRules, b.getAllExcludeRules)

  def getExcludeRules(moduleConfigurations: String) = concat(a.getExcludeRules(moduleConfigurations), b.getExcludeRules(moduleConfigurations))

  def getExcludeRules(moduleConfigurations: Array[String]) = concat(a.getExcludeRules(moduleConfigurations), b.getExcludeRules(moduleConfigurations))

  def doesExclude(moduleConfigurations: Array[String], artifactId: ArtifactId) = a.doesExclude(moduleConfigurations, artifactId) || b.doesExclude(moduleConfigurations, artifactId)

  def canExclude = a.canExclude || b.canExclude

  def asSystem = this

  def clone(revision: ModuleRevisionId) = new MergedDescriptors(a.clone(revision), b.clone(revision))

  def getAttribute(name: String): String = a.getAttribute(name)
  def getAttributes = a.getAttributes
  def getExtraAttribute(name: String) = a.getExtraAttribute(name)
  def getExtraAttributes = a.getExtraAttributes
  def getQualifiedExtraAttributes = a.getQualifiedExtraAttributes
  def getSourceModule = a.getSourceModule
}
