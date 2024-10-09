package sbt.internal.librarymanagement

import org.apache.ivy.util.Message
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.io.IO

object ManagedChecksumsSpec extends BaseIvySpecification {
  private final def targetDir = Some(currentDependency)
  private final def onlineConf = makeUpdateConfiguration(false, targetDir)
  private final def warningConf = UnresolvedWarningConfiguration()
  private final val Checksum = "sha1"

  def avro177 = ModuleID("org.apache.avro", "avro", "1.7.7")
  def dataAvro1940 = ModuleID("com.linkedin.pegasus", "data-avro", "1.9.40")
  def netty320 = ModuleID("org.jboss.netty", "netty", "3.2.0.Final")
  final def dependencies: Vector[ModuleID] =
    Vector(avro177, dataAvro1940, netty320).map(_.withConfigurations(Some("compile")))

  import sbt.io.syntax._
  override def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val moduleConfs = Vector(ModuleConfiguration("*", chainResolver))
    val resCacheDir = currentTarget / "resolution-cache"
    InlineIvyConfiguration()
      .withPaths(IvyPaths(currentBase.toString, Some(currentTarget.toString)))
      .withResolvers(resolvers)
      .withModuleConfigurations(moduleConfs)
      .withChecksums(Vector(Checksum))
      .withResolutionCacheDir(resCacheDir)
      .withLog(log)
      .withUpdateOptions(uo)
      .withManagedChecksums(true)
  }

  def cleanAll(): Unit = {
    cleanIvyCache()
    IO.delete(currentTarget)
    IO.delete(currentManaged)
    IO.delete(currentDependency)
  }

  def assertChecksumExists(file: File) = {
    val shaFile = new File(file.getAbsolutePath + s".$Checksum")
    Message.info(s"Checking $shaFile exists...")
    assert(shaFile.exists(), s"The checksum $Checksum for $file does not exist")
  }

  test("Managed checksums should should download the checksum files") {
    cleanAll()
    val updateOptions = UpdateOptions()
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
    val res = IvyActions.updateEither(toResolve, onlineConf, warningConf, log)
    assert(res.isRight, s"Resolution with managed checksums failed! $res")
    val updateReport = res.fold(e => throw e.resolveException, identity)
    val allModuleReports = updateReport.configurations.flatMap(_.modules)
    val allArtifacts: Seq[File] = allModuleReports.flatMap(_.artifacts.map(_._2))
    allArtifacts.foreach(assertChecksumExists)
  }
}
