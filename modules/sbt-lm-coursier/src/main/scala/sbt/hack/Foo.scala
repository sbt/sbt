package sbt.hack

import sbt._
import sbt.Keys._
import sbt.internal.LibraryManagement
import sbt.librarymanagement.DependencyResolution

import scala.language.reflectiveCalls

object Foo {

  // same implementation as update in sbt, except DependencyResolution is passed as argument
  // and the unmanagedJarsTask stuff was removed (already handled in lm-coursier via
  // SbtBootJars and all)
  def updateTask(lm: DependencyResolution): Def.Initialize[Task[UpdateReport]] = Def.task {
    val s = streams.value
    val cacheDirectory = streams.value.cacheDirectory

    val isRoot = executionRoots.value contains resolvedScoped.value
    val shouldForce = isRoot || {
      forceUpdatePeriod.value match {
        case None => false
        case Some(period) =>
          val fullUpdateOutput = cacheDirectory / "out"
          val now = System.currentTimeMillis
          val diff = now - fullUpdateOutput.lastModified()
          val elapsedDuration = new scala.concurrent.duration.FiniteDuration(diff, java.util.concurrent.TimeUnit.MILLISECONDS)
          fullUpdateOutput.exists() && elapsedDuration > period
      }
    }

    val state0 = state.value
    val updateConf = {
      // Log captures log messages at all levels, except ivy logs.
      // Use full level when debug is enabled so that ivy logs are shown.
      import UpdateLogging.{ Full, DownloadOnly, Default }
      val conf = updateConfiguration.value
      val maybeUpdateLevel = (logLevel in update).?.value
      val conf1 = maybeUpdateLevel.orElse(state0.get(logLevel.key)) match {
        case Some(Level.Debug) if conf.logging == Default => conf.withLogging(logging = Full)
        case Some(_) if conf.logging == Default           => conf.withLogging(logging = DownloadOnly)
        case _                                            => conf
      }

      // logical clock is folded into UpdateConfiguration
      conf1.withLogicalClock(LogicalClock(state0.hashCode))
    }

    val evictionOptions = Def.taskDyn {
      if (executionRoots.value.exists(_.key == evicted.key))
        Def.task(EvictionWarningOptions.empty)
      else Def.task((evictionWarningOptions in update).value)
    }.value

    try {
      LibraryManagement.cachedUpdate(
        // LM API
        lm = lm,
        // Ivy-free ModuleDescriptor
        module = ivyModule.value,
        s.cacheStoreFactory.sub(updateCacheName.value),
        Reference.display(thisProjectRef.value),
        updateConf,
        identity,
        skip = (skip in update).value,
        force = shouldForce,
        depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
        uwConfig = (unresolvedWarningConfiguration in update).value,
        ewo = evictionOptions,
        mavenStyle = publishMavenStyle.value,
        compatWarning = compatibilityWarningOptions.value,
        log = s.log
      )
    } catch {
      case _: NoSuchMethodError =>
        // cachedUpdate method changed in https://github.com/sbt/sbt/commit/6c7faf2b8611f122a37b824c6e08e950855d939f
        import sbt.internal.librarymanagement.CompatibilityWarningOptions
        import sbt.librarymanagement.{DependencyResolution, ModuleDescriptor, UnresolvedWarningConfiguration, UpdateConfiguration}
        import sbt.util.CacheStoreFactory
        LibraryManagement.asInstanceOf[{
          def cachedUpdate(
            lm: DependencyResolution,
            module: ModuleDescriptor,
            cacheStoreFactory: CacheStoreFactory,
            label: String,
            updateConfig: UpdateConfiguration,
            transform: UpdateReport => UpdateReport,
            skip: Boolean,
            force: Boolean,
            depsUpdated: Boolean,
            uwConfig: UnresolvedWarningConfiguration,
            ewo: EvictionWarningOptions,
            mavenStyle: Boolean,
            compatWarning: CompatibilityWarningOptions,
            includeCallers: Boolean,
            includeDetails: Boolean,
            log: Logger
          ): UpdateReport
        }].cachedUpdate(
          // LM API
          lm = lm,
          // Ivy-free ModuleDescriptor
          module = ivyModule.value,
          s.cacheStoreFactory.sub(updateCacheName.value),
          Reference.display(thisProjectRef.value),
          updateConf,
          identity,
          skip = (skip in update).value,
          force = shouldForce,
          depsUpdated = transitiveUpdate.value.exists(!_.stats.cached),
          uwConfig = (unresolvedWarningConfiguration in update).value,
          ewo = evictionOptions,
          mavenStyle = publishMavenStyle.value,
          compatWarning = compatibilityWarningOptions.value,
          includeCallers = false,
          includeDetails = false,
          log = s.log
        )
    }
  }

}
