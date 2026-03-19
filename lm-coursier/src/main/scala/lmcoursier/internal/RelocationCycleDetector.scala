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
private[internal] object RelocationCycleDetector:

  type Mvc = CoreResolution.ModuleVersionConstraint

  private def oneRelocationStep(resolution: Resolution, dep: Dependency): Option[Mvc] =
    resolution.reconciledVersions
      .get(dep.module)
      .flatMap: recon =>
        val dep0 =
          if dep.versionConstraint == recon then dep
          else dep.withVersionConstraint(recon)
        resolution.projectCache0
          .get(dep0.moduleVersionConstraint)
          .flatMap:
            case (_, proj) =>
              val mavenRelocatedOpt =
                if proj.relocated && proj.dependencies0.lengthCompare(1) == 0 then
                  Some(proj.dependencies0.head._2)
                else None
              def gradleRelocated: Option[Dependency] =
                dep0.variantSelector match
                  case attr: VariantSelector.AttributesBased =>
                    if proj.variants.isEmpty then None
                    else
                      proj.variantFor(attr) match
                        case Left(_)        => None
                        case Right(variant) => proj.isRelocatedVariant(variant)
                  case _: VariantSelector.ConfigurationBased => None
              mavenRelocatedOpt
                .orElse(gradleRelocated)
                .map: relocatedTo =>
                  val relocatedTo0 =
                    if relocatedTo.variantSelector.isEmpty then
                      relocatedTo.withVariantSelector(dep0.variantSelector)
                    else relocatedTo
                  relocatedTo0.moduleVersionConstraint

  /** When true, `coursier.graph.Conflict(resolution)` can run indefinitely. */
  def hasRelocationCycle(resolution: Resolution): Boolean =
    resolution.isDone && resolution.conflicts.isEmpty && resolution.errors0.isEmpty && {
      val keys = resolution.projectCache0.keySet
      keys.exists: start =>
        @tailrec
        def walk(visited: Set[Mvc], cur: Option[Mvc]): Boolean =
          cur match
            case None => false
            case Some(mvc) =>
              if visited.contains(mvc) then true
              else
                val dep = Dependency(module = mvc._1, version = mvc._2)
                walk(visited + mvc, oneRelocationStep(resolution, dep))
        walk(Set.empty, Some(start))
    }

end RelocationCycleDetector
