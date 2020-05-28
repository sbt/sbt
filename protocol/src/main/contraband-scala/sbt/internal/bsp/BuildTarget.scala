/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Build target
 * @param id The target’s unique identifier
 * @param displayName A human readable name for this target.
                      May be presented in the user interface.
                      Should be unique if possible.
                      The id.uri is used if None.
 * @param baseDirectory The directory where this target belongs to. Multiple build targets are allowed to map
                        to the same base directory, and a build target is not required to have a base directory.
                        A base directory does not determine the sources of a target, see buildTarget/sources.
 * @param tags Free-form string tags to categorize or label this build target.
               For example, can be used by the client to:
               - customize how the target should be translated into the client's project model.
               - group together different but related targets in the user interface.
               - display icons or colors in the user interface.
               Pre-defined tags are listed in `BuildTargetTag` but clients and servers
               are free to define new tags for custom purposes.
 * @param capabilities The capabilities of this build target.
 * @param languageIds The set of languages that this target contains.
                      The ID string for each language is defined in the LSP.
 * @param dependencies The direct upstream build target dependencies of this build target
 * @param dataKind Kind of data to expect in the `data` field. If this field is not set, the kind of data is not specified.
 * @param data Language-specific metadata about this target.
               See ScalaBuildTarget as an example.
 */
final class BuildTarget private (
  val id: sbt.internal.bsp.BuildTargetIdentifier,
  val displayName: Option[String],
  val baseDirectory: Option[java.net.URI],
  val tags: Vector[String],
  val capabilities: sbt.internal.bsp.BuildTargetCapabilities,
  val languageIds: Vector[String],
  val dependencies: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: BuildTarget => (this.id == x.id) && (this.displayName == x.displayName) && (this.baseDirectory == x.baseDirectory) && (this.tags == x.tags) && (this.capabilities == x.capabilities) && (this.languageIds == x.languageIds) && (this.dependencies == x.dependencies) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BuildTarget".##) + id.##) + displayName.##) + baseDirectory.##) + tags.##) + capabilities.##) + languageIds.##) + dependencies.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "BuildTarget(" + id + ", " + displayName + ", " + baseDirectory + ", " + tags + ", " + capabilities + ", " + languageIds + ", " + dependencies + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(id: sbt.internal.bsp.BuildTargetIdentifier = id, displayName: Option[String] = displayName, baseDirectory: Option[java.net.URI] = baseDirectory, tags: Vector[String] = tags, capabilities: sbt.internal.bsp.BuildTargetCapabilities = capabilities, languageIds: Vector[String] = languageIds, dependencies: Vector[sbt.internal.bsp.BuildTargetIdentifier] = dependencies, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): BuildTarget = {
    new BuildTarget(id, displayName, baseDirectory, tags, capabilities, languageIds, dependencies, dataKind, data)
  }
  def withId(id: sbt.internal.bsp.BuildTargetIdentifier): BuildTarget = {
    copy(id = id)
  }
  def withDisplayName(displayName: Option[String]): BuildTarget = {
    copy(displayName = displayName)
  }
  def withDisplayName(displayName: String): BuildTarget = {
    copy(displayName = Option(displayName))
  }
  def withBaseDirectory(baseDirectory: Option[java.net.URI]): BuildTarget = {
    copy(baseDirectory = baseDirectory)
  }
  def withBaseDirectory(baseDirectory: java.net.URI): BuildTarget = {
    copy(baseDirectory = Option(baseDirectory))
  }
  def withTags(tags: Vector[String]): BuildTarget = {
    copy(tags = tags)
  }
  def withCapabilities(capabilities: sbt.internal.bsp.BuildTargetCapabilities): BuildTarget = {
    copy(capabilities = capabilities)
  }
  def withLanguageIds(languageIds: Vector[String]): BuildTarget = {
    copy(languageIds = languageIds)
  }
  def withDependencies(dependencies: Vector[sbt.internal.bsp.BuildTargetIdentifier]): BuildTarget = {
    copy(dependencies = dependencies)
  }
  def withDataKind(dataKind: Option[String]): BuildTarget = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): BuildTarget = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): BuildTarget = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): BuildTarget = {
    copy(data = Option(data))
  }
}
object BuildTarget {
  
  def apply(id: sbt.internal.bsp.BuildTargetIdentifier, displayName: Option[String], baseDirectory: Option[java.net.URI], tags: Vector[String], capabilities: sbt.internal.bsp.BuildTargetCapabilities, languageIds: Vector[String], dependencies: Vector[sbt.internal.bsp.BuildTargetIdentifier], dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): BuildTarget = new BuildTarget(id, displayName, baseDirectory, tags, capabilities, languageIds, dependencies, dataKind, data)
  def apply(id: sbt.internal.bsp.BuildTargetIdentifier, displayName: String, baseDirectory: java.net.URI, tags: Vector[String], capabilities: sbt.internal.bsp.BuildTargetCapabilities, languageIds: Vector[String], dependencies: Vector[sbt.internal.bsp.BuildTargetIdentifier], dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): BuildTarget = new BuildTarget(id, Option(displayName), Option(baseDirectory), tags, capabilities, languageIds, dependencies, Option(dataKind), Option(data))
}
