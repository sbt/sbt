package sbt.internal.librarymanagement

import sbt.librarymanagement.*
import sbt.librarymanagement.ivy.UpdateOptions
import sbt.io.IO

object OfflineModeSpec extends BaseIvySpecification {
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

  def checkOnlineAndOfflineResolution(updateOptions: UpdateOptions): Unit = {
    cleanAll()
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
    if (updateOptions.cachedResolution)
      cleanCachedResolutionCache(toResolve)

    val onlineResolution =
      IvyActions.updateEither(toResolve, onlineConf, warningConf, log)
    assert(onlineResolution.isRight)
    assert(onlineResolution.toOption.exists(report => report.stats.resolveTime > 0))

    val originalResolveTime =
      onlineResolution.fold(e => throw e.resolveException, identity).stats.resolveTime
    val offlineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, log)
    assert(offlineResolution.isRight)

    val resolveTime =
      offlineResolution.fold(e => throw e.resolveException, identity).stats.resolveTime
    assert(originalResolveTime > resolveTime)
  }

  test("Offline update configuration should reuse the caches when offline is enabled") {
    checkOnlineAndOfflineResolution(normalOptions)
  }

  test("it should reuse the caches when offline and cached resolution are enabled") {
    checkOnlineAndOfflineResolution(cachedOptions)
  }

  def checkFailingResolution(updateOptions: UpdateOptions): Unit = {
    cleanAll()
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
    if (updateOptions.cachedResolution) cleanCachedResolutionCache(toResolve)
    val failedOfflineResolution =
      IvyActions.updateEither(toResolve, offlineConf, warningConf, log)
    assert(failedOfflineResolution.isLeft)
  }

  test("it should fail when artifacts are missing in the cache") {
    checkFailingResolution(normalOptions)
  }

  test("it should fail when artifacts are missing in the cache for cached resolution") {
    checkFailingResolution(cachedOptions)
  }
}
