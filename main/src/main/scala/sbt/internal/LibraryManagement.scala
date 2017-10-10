/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal

import java.io.File

import sbt.internal.librarymanagement._
import sbt.librarymanagement._
import sbt.librarymanagement.syntax._
import sbt.util.{ CacheStore, CacheStoreFactory, Logger, Tracked }

private[sbt] object LibraryManagement {

  private type UpdateInputs = (Long, ModuleSettings, UpdateConfiguration)

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
      log: Logger
  ): UpdateReport = {

    /* Resolve the module settings from the inputs. */
    def resolve(inputs: UpdateInputs): UpdateReport = {
      import sbt.util.ShowLines._

      log.info(s"Updating $label...")
      val reportOrUnresolved: Either[UnresolvedWarning, UpdateReport] =
        //try {
        lm.update(module, updateConfig, uwConfig, log)
      // } catch {
      //   case e: Throwable =>
      //     e.printStackTrace
      //     throw e
      // }
      val report = reportOrUnresolved match {
        case Right(report0) => report0
        case Left(unresolvedWarning) =>
          unresolvedWarning.lines.foreach(log.warn(_))
          throw unresolvedWarning.resolveException
      }
      log.info("Done updating.")
      val finalReport = transform(report)

      // Warn of any eviction and compatibility warnings
      val ew = EvictionWarning(module, ewo, finalReport, log)
      ew.lines.foreach(log.warn(_))
      ew.infoAllTheThings.foreach(log.info(_))
      CompatibilityWarning.run(compatWarning, module, mavenStyle, log)

      finalReport
    }

    /* Check if a update report is still up to date or we must resolve again. */
    def upToDate(inChanged: Boolean, out: UpdateReport): Boolean = {
      !force &&
      !depsUpdated &&
      !inChanged &&
      out.allFiles.forall(f => fileUptodate(f, out.stamps)) &&
      fileUptodate(out.cachedDescriptor, out.stamps)
    }

    /* Skip resolve if last output exists, otherwise error. */
    def skipResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      import sbt.librarymanagement.LibraryManagementCodec._
      Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
        case (_, Some(out)) => markAsCached(out)
        case _ =>
          sys.error("Skipping update requested, but update has not previously run successfully.")
      }
    }

    // Mark UpdateReport#stats as "cached." This is used by the dependers later
    // to determine whether they now need to run update in the above `upToDate`.
    def markAsCached(ur: UpdateReport): UpdateReport =
      ur.withStats(ur.stats.withCached(true))

    def doResolve(cache: CacheStore): UpdateInputs => UpdateReport = {
      val doCachedResolve = { (inChanged: Boolean, updateInputs: UpdateInputs) =>
        import sbt.librarymanagement.LibraryManagementCodec._
        val cachedResolve = Tracked.lastOutput[UpdateInputs, UpdateReport](cache) {
          case (_, Some(out)) if upToDate(inChanged, out) => markAsCached(out)
          case _                                          => resolve(updateInputs)
        }
        import scala.util.control.Exception.catching
        catching(classOf[NullPointerException], classOf[OutOfMemoryError])
          .withApply { t =>
            val resolvedAgain = resolve(updateInputs)
            val culprit = t.getClass.getSimpleName
            log.warn(s"Update task caching failed due to $culprit.")
            log.warn("Report the following output to sbt:")
            resolvedAgain.toString.lines.foreach(log.warn(_))
            log.trace(t)
            resolvedAgain
          }
          .apply(cachedResolve(updateInputs))
      }
      import LibraryManagementCodec._
      Tracked.inputChanged(cacheStoreFactory.make("inputs"))(doCachedResolve)
    }

    // Get the handler to use and feed it in the inputs
    // This is lm-engine specific input hashed into Long
    val extraInputHash = module.extraInputHash
    val settings = module.moduleSettings
    val outStore = cacheStoreFactory.make("output")
    val handler = if (skip && !force) skipResolve(outStore) else doResolve(outStore)
    // Remove clock for caching purpose
    val withoutClock = updateConfig.withLogicalClock(LogicalClock.unknown)
    handler((extraInputHash, settings, withoutClock))
  }

  private[this] def fileUptodate(file: File, stamps: Map[File, Long]): Boolean =
    stamps.get(file).forall(_ == file.lastModified)

  private[sbt] def transitiveScratch(
      lm: DependencyResolution,
      label: String,
      config: GetClassifiersConfiguration,
      uwconfig: UnresolvedWarningConfiguration,
      log: Logger
  ): Either[UnresolvedWarning, UpdateReport] = {
    import config.{ updateConfiguration => c, module => mod }
    import mod.{ id, dependencies => deps, scalaModuleInfo }
    val base = restrictedCopy(id, true).withName(id.name + "$" + label)
    val module = lm.moduleDescriptor(base, deps, scalaModuleInfo)
    val report = lm.update(module, c, uwconfig, log) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }
    val newConfig = config
      .withModule(mod.withDependencies(report.allModules))
    lm.updateClassifiers(newConfig, uwconfig, Vector(), log)
  }

  private[sbt] def restrictedCopy(m: ModuleID, confs: Boolean) =
    ModuleID(m.organization, m.name, m.revision)
      .withCrossVersion(m.crossVersion)
      .withExtraAttributes(m.extraAttributes)
      .withConfigurations(if (confs) m.configurations else None)
      .branch(m.branchName)
}
