package sbt
package dependencygraph

import scala.language.implicitConversions

import java.util.concurrent.TimeUnit

import Keys._
import Def.Initialize
import CrossVersion._

import scala.concurrent.duration.FiniteDuration

object DependencyGraphSbtCompat {
  object Implicits {
    implicit def convertConfig(config: sbt.Configuration): String = config.toString

    implicit class RichUpdateConfiguration(val updateConfig: UpdateConfiguration) extends AnyVal {
      def withMissingOk(missingOk: Boolean): UpdateConfiguration =
        updateConfig.copy(missingOk = missingOk)
    }
  }

  /**
    * This is copied directly from https://github.com/sbt/sbt/blob/2952a2b9b672c5402b824ad2d2076243eb643598/main/src/main/scala/sbt/Defaults.scala#L1471-L1523
    * and then changed to update the UpdateConfiguration to ignore missing artifacts.
    */
  def updateTask(task: TaskKey[_]): Initialize[Task[UpdateReport]] = Def.task {
    val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
    val isRoot = executionRoots.value contains resolvedScoped.value
    val forceUpdate = forceUpdatePeriod.value
    val s = streams.value
    val fullUpdateOutput = s.cacheDirectory / "out"
    val forceUpdateByTime = forceUpdate match {
      case None => false
      case Some(period) =>
        val elapsedDuration = new FiniteDuration(System.currentTimeMillis() - fullUpdateOutput.lastModified(), TimeUnit.MILLISECONDS)
        fullUpdateOutput.exists() && elapsedDuration > period
    }
    val scalaProvider = appConfiguration.value.provider.scalaProvider

    // Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
    //   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
    //   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
    def subUnmanaged(subVersion: String, jars: Seq[File]) = (sv: String) =>
      (partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion.value)) match {
        case (Some(res), Some(sh), _) if res == sh     => jars
        case (Some(res), _, Some(decl)) if res == decl => jars
        case _                                         => Nil
      }
    val subScalaJars: String => Seq[File] = Defaults.unmanagedScalaInstanceOnly.value match {
      case Some(si) => subUnmanaged(si.version, si.jars)
      case None     => sv => if (scalaProvider.version == sv) scalaProvider.jars else Nil
    }
    val transform: UpdateReport => UpdateReport = r => sbt.Classpaths.substituteScalaFiles(scalaOrganization.value, r)(subScalaJars)
    val uwConfig = (unresolvedWarningConfiguration in update).value
    val show = Reference.display(thisProjectRef.value)
    val st = state.value
    val logicalClock = LogicalClock(st.hashCode)
    val depDir = dependencyCacheDirectory.value
    val uc0 = (updateConfiguration in task).value
    val ms = publishMavenStyle.value
    val cw = compatibilityWarningOptions.value
    // Normally, log would capture log messages at all levels.
    // Ivy logs are treated specially using sbt.UpdateConfiguration.logging.
    // This code bumps up the sbt.UpdateConfiguration.logging to Full when logLevel is Debug.
    import UpdateLogging.{ Full, DownloadOnly, Default }
    val uc = (logLevel in update).?.value orElse st.get(logLevel.key) match {
      case Some(Level.Debug) if uc0.logging == Default => uc0.copy(logging = Full)
      case Some(x) if uc0.logging == Default => uc0.copy(logging = DownloadOnly)
      case _ => uc0
    }
    val ewo =
      if (executionRoots.value exists { _.key == evicted.key }) EvictionWarningOptions.empty
      else (evictionWarningOptions in update).value
    sbt.Classpaths.cachedUpdate(s.cacheDirectory / updateCacheName.value, show, ivyModule.value, uc, transform,
      skip = (skip in update).value, force = isRoot || forceUpdateByTime, depsUpdated = depsUpdated,
      uwConfig = uwConfig, logicalClock = logicalClock, depDir = Some(depDir),
      ewo = ewo, mavenStyle = ms, compatWarning = cw, log = s.log)
  }
}
