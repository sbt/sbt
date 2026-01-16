package sbt.internal.librarymanagement

import java.io.File
import sbt.librarymanagement.*

object UpdateClassifiersUtil {

  def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision)
      .withCrossVersion(m.crossVersion)
      .withExtraAttributes(m.extraAttributes)
      .withConfigurations(if (confs) m.configurations else None)
      .branch(m.branchName)

  // This version adds explicit artifact
  def classifiedArtifacts(
      classifiers: Vector[String],
      exclude: Map[ModuleID, Set[ConfigRef]],
      artifacts: Vector[(String, ModuleID, Artifact, File)]
  )(m: ModuleID): Option[ModuleID] = {
    def sameModule(m1: ModuleID, m2: ModuleID): Boolean =
      m1.organization == m2.organization && m1.name == m2.name && m1.revision == m2.revision
    def explicitArtifacts = {
      val arts = (artifacts collect {
        case (_, x, art, _) if sameModule(m, x) && art.classifier.isDefined => art
      }).distinct
      if (arts.isEmpty) None
      else Some(intransitiveModuleWithExplicitArts(m, arts))
    }
    def hardcodedArtifacts = classifiedArtifacts(classifiers, exclude)(m)
    explicitArtifacts orElse hardcodedArtifacts
  }

  def classifiedArtifacts(
      classifiers: Vector[String],
      exclude: Map[ModuleID, Set[ConfigRef]]
  )(m: ModuleID): Option[ModuleID] = {
    val excluded: Set[ConfigRef] = exclude.getOrElse(restrictedCopy(m, false), Set.empty)
    val exls = excluded map { _.name }
    val included = classifiers filterNot exls
    if (included.isEmpty) None
    else {
      Some(
        intransitiveModuleWithExplicitArts(
          module = m,
          arts = classifiedArtifacts(m.name, included)
        )
      )
    }
  }

  def classifiedArtifacts(name: String, classifiers: Vector[String]): Vector[Artifact] =
    classifiers map { c =>
      Artifact.classified(name, c)
    }

  /**
   * Explicitly set an "include all" rule (the default) because otherwise, if we declare ANY explicitArtifacts,
   * [[org.apache.ivy.core.resolve.IvyNode#getArtifacts]] (in Ivy 2.3.0-rc1) will not merge in the descriptor's
   * artifacts and will only keep the explicitArtifacts.
   * <br>
   * Look for the comment saying {{{
   *   // and now we filter according to include rules
   * }}}
   * in `IvyNode`, which iterates on `includes`, which will ordinarily be empty because higher up, in {{{
   *   addAllIfNotNull(includes, usage.getDependencyIncludesSet(rootModuleConf));
   * }}}
   * `usage.getDependencyIncludesSet` returns null if there are no (explicit) include rules.
   */
  private def intransitiveModuleWithExplicitArts(
      module: ModuleID,
      arts: Vector[Artifact]
  ): ModuleID =
    module
      .withIsTransitive(false)
      .withExplicitArtifacts(arts)
      .withInclusions(Vector(InclExclRule.everything))

}
