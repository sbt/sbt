package sbt.librarymanagement

import org.scalatest.Assertion
import sbt.internal.librarymanagement._
import sbt.internal.librarymanagement.impl.DependencyBuilders

class OfflineModeSpec extends BaseIvySpecification with DependencyBuilders {
  private final val targetDir = Some(currentDependency)
  private final val onlineConf = makeUpdateConfiguration(false)
  private final val offlineConf = makeUpdateConfiguration(true)
  private final val noClock = LogicalClock.unknown
  private final val warningConf = UnresolvedWarningConfiguration()
  private final val normalOptions = UpdateOptions()
  private final val cachedOptions = UpdateOptions().withCachedResolution(true)

  final val scalaCompiler = Vector("org.scala-lang" % "scala-compiler" % "2.12.2" % "compile")

  def checkOnlineAndOfflineResolution(updateOptions: UpdateOptions): Assertion = {
    cleanIvyCache()

    val toResolve = module(defaultModuleId, scalaCompiler, None, updateOptions)
    val isCachedResolution = updateOptions.cachedResolution
    if (isCachedResolution) cleanCachedResolutionCache(toResolve)

    val onlineResolution =
      IvyActions.updateEither(toResolve, onlineConf, warningConf, noClock, targetDir, log)
    assert(onlineResolution.isRight)
    assert(onlineResolution.right.exists(report => report.stats.resolveTime > 0))

    // Compute an estimate to ensure that the second resolution does indeed use the cache
    val resolutionTime: Long = onlineResolution.right.map(_.stats.resolveTime).getOrElse(0L)
    val estimatedCachedTime = resolutionTime * 0.15

    val offlineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, noClock, targetDir, log)
    assert(offlineResolution.isRight)

    if (!isCachedResolution) {
      // Only check the estimate for the non cached resolution, otherwise resolution is cached
      assert(offlineResolution.right.exists(_.stats.resolveTime <= estimatedCachedTime),
             "Offline resolution took more than 15% of normal resolution's running time.")
    } else assert(true) // We cannot check offline resolution if it's cached.
  }

  "Offline update configuration" should "reuse the caches when it's enabled" in {
    checkOnlineAndOfflineResolution(normalOptions)
  }

  it should "work with cached resolution" in {
    checkOnlineAndOfflineResolution(cachedOptions)
  }

  def checkFailingResolution(updateOptions: UpdateOptions): Assertion = {
    cleanIvyCache()
    val toResolve = module(defaultModuleId, scalaCompiler, None, updateOptions)
    if (updateOptions.cachedResolution) cleanCachedResolutionCache(toResolve)
    val failedOfflineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, noClock, targetDir, log)
    assert(failedOfflineResolution.isLeft)
  }

  it should "fail when artifacts are missing in the cache" in {
    checkFailingResolution(normalOptions)
  }

  it should "fail when artifacts are missing in the cache for cached resolution" in {
    checkFailingResolution(cachedOptions)
  }
}
