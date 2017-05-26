package sbt.librarymanagement

import java.io.File

import org.apache.ivy.util.Message
import org.scalatest.Assertion
import sbt.internal.librarymanagement.{
  BaseIvySpecification,
  InlineIvyConfiguration,
  IvyActions,
  IvyConfiguration,
  IvyPaths,
  IvySbt,
  LogicalClock,
  UnresolvedWarningConfiguration
}
import sbt.internal.librarymanagement.impl.DependencyBuilders
import sbt.io.IO

class ManagedChecksumsSpec extends BaseIvySpecification with DependencyBuilders {
  private final def targetDir = Some(currentDependency)
  private final def onlineConf = makeUpdateConfiguration(false)
  private final def warningConf = UnresolvedWarningConfiguration()
  private final def noClock = LogicalClock.unknown
  private final val Checksum = "sha1"

  def avro177 = ModuleID("org.apache.avro", "avro", "1.7.7")
  def dataAvro1940 = ModuleID("com.linkedin.pegasus", "data-avro", "1.9.40")
  def netty320 = ModuleID("org.jboss.netty", "netty", "3.2.0.Final")
  final def dependencies: Vector[ModuleID] =
    Vector(avro177, dataAvro1940, netty320).map(_.withConfigurations(Some("compile")))

  import sbt.io.syntax._
  override def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val paths = IvyPaths(currentBase, Some(currentTarget))
    val other = Vector.empty
    val check = Vector(Checksum)
    val moduleConfs = Vector(ModuleConfiguration("*", chainResolver))
    val resCacheDir = currentTarget / "resolution-cache"
    new InlineIvyConfiguration(paths,
                               resolvers,
                               other,
                               moduleConfs,
                               None,
                               check,
                               Some(resCacheDir),
                               uo,
                               log)
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

  "Managed checksums" should "should download the checksum files" in {
    cleanAll()
    val updateOptions = UpdateOptions().withManagedChecksums(true)
    val toResolve = module(defaultModuleId, dependencies, None, updateOptions)
    val res = IvyActions.updateEither(toResolve, onlineConf, warningConf, noClock, targetDir, log)
    assert(res.isRight, s"Resolution with managed checksums failed! $res")
    val updateReport = res.right.get
    val allModuleReports = updateReport.configurations.flatMap(_.modules)
    val allArtifacts: Seq[File] = allModuleReports.flatMap(_.artifacts.map(_._2))
    allArtifacts.foreach(assertChecksumExists)
  }
}
