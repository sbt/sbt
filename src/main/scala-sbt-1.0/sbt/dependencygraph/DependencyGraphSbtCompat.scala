package sbt
package dependencygraph

import Keys._
import Def.Initialize

import CrossVersion.partialVersion
import sbt.internal.LibraryManagement

object DependencyGraphSbtCompat {
  object Implicits

  // https://github.com/sbt/sbt/blob/4ce4fb72bde3b8acfaf526b79d32ca1463bc687b/main/src/main/scala/sbt/Defaults.scala#L2298 adapted
  // to allow customization of UpdateConfiguration
  def updateTask(task: TaskKey[_]): Initialize[Task[UpdateReport]] = Def.task {
    val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
    val isRoot = executionRoots.value contains resolvedScoped.value
    val s = streams.value
    val scalaProvider = appConfiguration.value.provider.scalaProvider

    // Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
    //   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
    //   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
    def subUnmanaged(subVersion: String, jars: Seq[File]) = (sv: String) ⇒
      (partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion.value)) match {
        case (Some(res), Some(sh), _) if res == sh     ⇒ jars
        case (Some(res), _, Some(decl)) if res == decl ⇒ jars
        case _                                         ⇒ Nil
      }

    val subScalaJars: String ⇒ Seq[File] = SbtAccess.unmanagedScalaInstanceOnly.value match {
      case Some(si) ⇒ subUnmanaged(si.version, si.allJars)
      case None     ⇒ sv ⇒ if (scalaProvider.version == sv) scalaProvider.jars else Nil
    }

    val transform: UpdateReport ⇒ UpdateReport =
      r ⇒ Classpaths.substituteScalaFiles(scalaOrganization.value, r)(subScalaJars)

    val evictionOptions = Def.taskDyn {
      if (executionRoots.value.exists(_.key == evicted.key))
        Def.task(EvictionWarningOptions.empty)
      else Def.task((evictionWarningOptions in update).value)
    }.value

    LibraryManagement.cachedUpdate(
      // LM API
      lm = dependencyResolution.value,
      // Ivy-free ModuleDescriptor
      module = ivyModule.value,
      s.cacheStoreFactory.sub(updateCacheName.value),
      Reference.display(thisProjectRef.value),
      (updateConfiguration in task).value,
      transform = transform,
      skip = (skip in update).value,
      force = isRoot,
      depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
      uwConfig = (unresolvedWarningConfiguration in update).value,
      ewo = evictionOptions,
      mavenStyle = publishMavenStyle.value,
      compatWarning = compatibilityWarningOptions.value,
      log = s.log
    )
  }
}
