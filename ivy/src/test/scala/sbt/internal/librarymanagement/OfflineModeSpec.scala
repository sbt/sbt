package sbt.internal.librarymanagement

import sbt.librarymanagement._
import sbt.librarymanagement.ivy.UpdateOptions
import org.scalatest.{ Assertion, DiagrammedAssertions }
import sbt.io.IO

class OfflineModeSpec extends BaseIvySpecification with DiagrammedAssertions {
  private final def targetDir = Some(currentDependency)
  private final def onlineConf = makeUpdateConfiguration(false, targetDir)
  private final def offlineConf = makeUpdateConfiguration(true, targetDir)
  private final def warningConf = UnresolvedWarningConfiguration()
  private final def normalOptions = UpdateOptions()
  private final def cachedOptions = UpdateOptions().withCachedResolution(true)

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
      IvyActions.updateEither(toResolve, onlineConf, warningConf, log)
    assert(onlineResolution.isRight)
    assert(onlineResolution.right.exists(report => report.stats.resolveTime > 0))

    val originalResolveTime = onlineResolution.right.get.stats.resolveTime
    val offlineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, log)
    assert(offlineResolution.isRight)

    val resolveTime = offlineResolution.right.get.stats.resolveTime
    assert(originalResolveTime > resolveTime)
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
      IvyActions.updateEither(toResolve, offlineConf, warningConf, log)
    assert(failedOfflineResolution.isLeft)
  }

  it should "fail when artifacts are missing in the cache" in {
    checkFailingResolution(normalOptions)
  }

  it should "fail when artifacts are missing in the cache for cached resolution" in {
    checkFailingResolution(cachedOptions)
  }
}
