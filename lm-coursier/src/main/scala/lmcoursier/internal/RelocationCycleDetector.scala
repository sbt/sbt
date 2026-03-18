package lmcoursier.internal

import coursier.core.*
import coursier.core.Resolution as CoreResolution
import coursier.{ Dependency, Resolution }

import scala.annotation.tailrec

/**
 * Detects cyclic Maven / Gradle relocation chains that make
 * `coursier.graph.DependencyTree` loop forever (see sbt#8917, coursier#3578).
 *
 * Mirrors one step of `coursier.graph.DependencyTree.Node.relocation` so we
 * only skip `Conflict` when Coursier would spin on the same graph.
 */
private[internal] object RelocationCycleDetector {

  type Mvc = CoreResolution.ModuleVersionConstraint

  private def oneRelocationStep(resolution: Resolution, dep: Dependency): Option[Mvc] = {
    val reconciledVersion =
      resolution.reconciledVersions.get(dep.module) match {
        case Some(v) => v
        case None    => return None
      }
    val dep0 =
      if (dep.versionConstraint == reconciledVersion) dep
      else dep.withVersionConstraint(reconciledVersion)
    val (_, proj) =
      resolution.projectCache0.get(dep0.moduleVersionConstraint) match {
        case Some(v) => v
        case None    => return None
      }
    val mavenRelocatedOpt =
      if (proj.relocated && proj.dependencies0.lengthCompare(1) == 0)
        Some(proj.dependencies0.head._2)
      else None
    def gradleModuleRelocatedOpt =
      dep0.variantSelector match {
        case attr: VariantSelector.AttributesBased =>
          if (proj.variants.isEmpty) None
          else
            proj.variantFor(attr) match {
              case Left(_)        => None
              case Right(variant) => proj.isRelocatedVariant(variant)
            }
        case _: VariantSelector.ConfigurationBased => None
      }
    mavenRelocatedOpt.orElse(gradleModuleRelocatedOpt) match {
      case Some(relocatedTo) =>
        val relocatedTo0 =
          if (relocatedTo.variantSelector.isEmpty)
            relocatedTo.withVariantSelector(dep0.variantSelector)
          else relocatedTo
        Some(relocatedTo0.moduleVersionConstraint)
      case None => None
    }
  }

  /** When true, `coursier.graph.Conflict(resolution)` can run indefinitely. */
  def hasRelocationCycle(resolution: Resolution): Boolean = {
    if (!resolution.isDone || resolution.conflicts.nonEmpty || resolution.errors0.nonEmpty)
      return false
    val keys = resolution.projectCache0.keySet
    keys.exists { start =>
      @tailrec
      def walk(visited: Set[Mvc], cur: Option[Mvc]): Boolean =
        cur match {
          case None => false
          case Some(mvc) =>
            if (visited.contains(mvc)) true
            else {
              val dep = Dependency(module = mvc._1, version = mvc._2)
              walk(visited + mvc, oneRelocationStep(resolution, dep))
            }
        }
      walk(Set.empty, Some(start))
    }
  }
}
