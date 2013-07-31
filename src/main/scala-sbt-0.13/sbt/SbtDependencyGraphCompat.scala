package sbt

import net.virtualvoid.sbt.graph.Plugin._
import Keys._
import CrossVersion._

object SbtDependencyGraphCompat {
  /**
   * This is copied directly from sbt/main/Defaults.java and then changed to update the UpdateConfiguration
   * to ignore missing artifacts.
   */
  def ignoreMissingUpdateT =
    ignoreMissingUpdate <<= Def.task {
      val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
      val isRoot = executionRoots.value contains resolvedScoped.value
      val s = streams.value
      val scalaProvider = appConfiguration.value.provider.scalaProvider

      // Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
      //   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
      //   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
      def subUnmanaged(subVersion: String, jars: Seq[File])  =  (sv: String) =>
        (partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion.value)) match {
          case (Some(res), Some(sh), _) if res == sh => jars
          case (Some(res), _, Some(decl)) if res == decl => jars
          case _ => Nil
        }
      val subScalaJars: String => Seq[File] = Defaults.unmanagedScalaInstanceOnly.value match {
        case Some(si) => subUnmanaged(si.version, si.jars)
        case None => sv => if(scalaProvider.version == sv) scalaProvider.jars else Nil
      }
      val transform: UpdateReport => UpdateReport = r => Classpaths.substituteScalaFiles(scalaOrganization.value, r)(subScalaJars)

      val show = Reference.display(thisProjectRef.value)
      Classpaths.cachedUpdate(s.cacheDirectory, show, ivyModule.value, (updateConfiguration in ignoreMissingUpdate).value, transform, skip = (skip in update).value, force = isRoot, depsUpdated = depsUpdated, log = s.log)
    }

  def getTerminalWidth: Int = JLine.usingTerminal(_.getWidth)
}
