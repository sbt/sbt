package sbt.librarymanagement

import org.scalatest.Assertion
import sbt.internal.librarymanagement._
import sbt.internal.librarymanagement.impl.DependencyBuilders
import sbt.io.{ FileFilter, IO, Path }

class OfflineModeSpec extends BaseIvySpecification with DependencyBuilders {
  private final def targetDir = Some(currentDependency)
  private final def onlineConf = makeUpdateConfiguration(false)
  private final def offlineConf = makeUpdateConfiguration(true)
  private final def warningConf = UnresolvedWarningConfiguration()
  private final def normalOptions = UpdateOptions()
  private final def cachedOptions = UpdateOptions().withCachedResolution(true)
  private final def noClock = LogicalClock.unknown

  def avro177 = ModuleID("org.apache.avro", "avro", "1.7.7")
  def dataAvro1940 = ModuleID("com.linkedin.pegasus", "data-avro", "1.9.40")
  def netty320 = ModuleID("org.jboss.netty", "netty", "3.2.0.Final")
  final def dependencies: Vector[ModuleID] =
    Vector(avro177, dataAvro1940, netty320).map(_.withConfigurations(Some("compile")))

  def cleanAll(): Unit = {
    cleanIvyCache()
    IO.delete(currentTarget)
    IO.delete(currentManaged)
    IO.delete(currentDependency)
  }

  def checkOnlineAndOfflineResolution(updateOptions: UpdateOptions): Assertion = {
    cleanAll()
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
    if (updateOptions.cachedResolution)
      cleanCachedResolutionCache(toResolve)

    val onlineResolution =
      IvyActions.updateEither(toResolve, onlineConf, warningConf, noClock, targetDir, log)
    assert(onlineResolution.isRight)
    assert(onlineResolution.right.exists(report => report.stats.resolveTime > 0))

    // Compute an estimate to ensure that the second resolution does indeed use the cache
    val originalResolveTime = onlineResolution.right.get.stats.resolveTime
    val estimatedCachedTime = originalResolveTime * 0.15

    val offlineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, noClock, targetDir, log)
    assert(offlineResolution.isRight, s"Offline resolution has failed with $offlineResolution.")

    val resolveTime = offlineResolution.right.get.stats.resolveTime
    // Only check the estimate for the non cached resolution, otherwise resolution is cached
    assert(resolveTime <= estimatedCachedTime,
           "Offline resolution took more than 15% of normal resolution's running time.")
  }

  "Offline update configuration" should "reuse the caches when offline is enabled" in {
    checkOnlineAndOfflineResolution(normalOptions)
  }

  it should "reuse the caches when offline and cached resolution are enabled" in {
    checkOnlineAndOfflineResolution(cachedOptions)
  }

  def checkFailingResolution(updateOptions: UpdateOptions): Assertion = {
    cleanAll()
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
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
