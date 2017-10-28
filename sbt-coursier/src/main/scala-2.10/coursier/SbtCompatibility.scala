package coursier

import scala.language.implicitConversions

object SbtCompatibility {

  final case class ConfigRef(name: String) extends AnyVal
  implicit def configRefToString(ref: ConfigRef): String = ref.name

  val GetClassifiersModule = sbt.GetClassifiersModule
  type GetClassifiersModule = sbt.GetClassifiersModule

  object SbtPomExtraProperties {
    def POM_INFO_KEY_PREFIX = sbt.mavenint.SbtPomExtraProperties.POM_INFO_KEY_PREFIX
  }

  type MavenRepository = sbt.MavenRepository

  type IvySbt = sbt.IvySbt

  implicit class ModuleIDOps(val id: sbt.ModuleID) extends AnyVal {
    def withConfigurations(configurations: Option[String]): sbt.ModuleID =
      id.copy(configurations = configurations)
    def withExtraAttributes(extraAttributes: Map[String, String]): sbt.ModuleID =
      id.copy(extraAttributes = extraAttributes)
  }

  implicit class ArtifactOps(val artifact: sbt.Artifact) extends AnyVal {
    def withType(`type`: String): sbt.Artifact =
      artifact.copy(`type` = `type`)
    def withExtension(extension: String): sbt.Artifact =
      artifact.copy(extension = extension)
    def withClassifier(classifier: Option[String]): sbt.Artifact =
      artifact.copy(classifier = classifier)
    def withUrl(url: Option[sbt.URL]): sbt.Artifact =
      artifact.copy(url = url)
    def withExtraAttributes(extraAttributes: Map[String, String]): sbt.Artifact =
      artifact.copy(extraAttributes = extraAttributes)
  }

  implicit def toModuleReportOps(report: sbt.ModuleReport): sbt.ModuleReportOps =
    new sbt.ModuleReportOps(report)

  implicit class ConfigurationOps(val config: sbt.Configuration) extends AnyVal {
    def withExtendsConfigs(extendsConfigs: Vector[sbt.Configuration]): sbt.Configuration =
      config.copy(extendsConfigs = extendsConfigs.toList)
    def toConfigRef: ConfigRef =
      ConfigRef(config.name)
  }

  implicit def configurationToConfigRef(config: sbt.Configuration): ConfigRef =
    config.toConfigRef

  implicit class ConfigurationCompanionOps(val companion: sbt.Configuration.type) extends AnyVal {
    def of(
      id: String,
      name: String,
      description: String,
      isPublic: Boolean,
      extendsConfigs: Vector[sbt.Configuration],
      transitive: Boolean
    ): sbt.Configuration =
      sbt.Configuration(name, description, isPublic, extendsConfigs.toList, transitive)
  }

  implicit class CallerCompanionOps(val companion: sbt.Caller.type) extends AnyVal {
    def apply(
      caller: sbt.ModuleID,
      callerConfigurations: Vector[ConfigRef],
      callerExtraAttributes: Map[String, String],
      isForceDependency: Boolean,
      isChangingDependency: Boolean,
      isTransitiveDependency: Boolean,
      isDirectlyForceDependency: Boolean
    ): sbt.Caller =
      new sbt.Caller(
        caller,
        callerConfigurations.map(_.name),
        callerExtraAttributes,
        isForceDependency,
        isChangingDependency,
        isTransitiveDependency,
        isDirectlyForceDependency
      )
  }

  implicit class ConfigurationReportCompanionOps(val companion: sbt.ConfigurationReport.type) extends AnyVal {
    def apply(
      configuration: String,
      modules: Seq[sbt.ModuleReport],
      details: Seq[sbt.OrganizationArtifactReport]
    ): sbt.ConfigurationReport =
      new sbt.ConfigurationReport(
        configuration,
        modules,
        details,
        Nil
      )
  }

  implicit class UpdateReportCompanionOps(val companion: sbt.UpdateReport.type) extends AnyVal {
    def apply(
      cachedDescriptor: java.io.File,
      configurations: Seq[sbt.ConfigurationReport],
      stats: sbt.UpdateStats,
      stamps: Map[java.io.File, Long]
    ): sbt.UpdateReport =
      new sbt.UpdateReport(
        cachedDescriptor,
        configurations,
        stats,
        stamps
      )
  }

  implicit class UpdateStatsCompanionOps(val companion: sbt.UpdateStats.type) extends AnyVal {
    def apply(
      resolveTime: Long,
      downloadTime: Long,
      downloadSize: Long,
      cached: Boolean
    ): sbt.UpdateStats =
      new sbt.UpdateStats(
        resolveTime,
        downloadTime,
        downloadSize,
        cached
      )
  }

  implicit def configVectorToList(configs: Vector[sbt.Configuration]): List[sbt.Configuration] =
    configs.toList
  implicit def configListToVector(configs: List[sbt.Configuration]): Vector[sbt.Configuration] =
    configs.toVector

  implicit class GetClassifiersModuleOps(val module: GetClassifiersModule) extends AnyVal {
    def dependencies = module.modules
  }

  def needsIvyXmlLocal = List(sbt.Keys.deliverLocalConfiguration)
  def needsIvyXml = List(sbt.Keys.deliverConfiguration)

}
