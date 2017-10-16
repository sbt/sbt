package sbt

import Keys._
import Def.Initialize

import CrossVersion.partialVersion

object Compat {

  // https://github.com/sbt/sbt/blob/e3c4db5ae80fa3e2a40b7a81bee0822e49f76aaf/main/src/main/scala/sbt/Defaults.scala#L1471
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

    val show = Reference.display(thisProjectRef.value)

    Classpaths.cachedUpdate(
      cacheFile = s.cacheDirectory,
      label = show,
      module = ivyModule.value,
      config = (updateConfiguration in task).value,
      transform = transform,
      skip = (skip in update).value,
      force = isRoot,
      depsUpdated = depsUpdated,
      log = s.log)
  }
}