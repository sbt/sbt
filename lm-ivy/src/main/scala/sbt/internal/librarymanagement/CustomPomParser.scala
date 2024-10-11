package sbt.internal.librarymanagement

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.{
  DefaultArtifact,
  DefaultExtendsDescriptor,
  DefaultModuleDescriptor,
  ModuleDescriptor
}
import org.apache.ivy.core.module.descriptor.{ DefaultDependencyDescriptor, DependencyDescriptor }
import org.apache.ivy.plugins.parser.{
  ModuleDescriptorParser,
  ModuleDescriptorParserRegistry,
  ParserSettings
}
import org.apache.ivy.plugins.parser.m2.{
  ReplaceMavenConfigurationMappings,
  PomModuleDescriptorBuilder,
  PomModuleDescriptorParser
}
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.namespace.NamespaceTransformer
import org.apache.ivy.util.extendable.ExtendableItem

import java.io.{ File, InputStream }
import java.net.URL
import sbt.internal.librarymanagement.mavenint.{
  PomExtraDependencyAttributes,
  SbtPomExtraProperties
}
import sbt.io.Hash
import scala.collection.immutable.ArraySeq

// @deprecated("We now use an Aether-based pom parser.", "0.13.8")
final class CustomPomParser(
    delegate: ModuleDescriptorParser,
    transform: (ModuleDescriptorParser, ModuleDescriptor) => ModuleDescriptor
) extends ModuleDescriptorParser {
  override def parseDescriptor(
      ivySettings: ParserSettings,
      descriptorURL: URL,
      validate: Boolean
  ) =
    transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, validate))

  override def parseDescriptor(
      ivySettings: ParserSettings,
      descriptorURL: URL,
      res: Resource,
      validate: Boolean
  ) =
    transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, res, validate))

  override def toIvyFile(is: InputStream, res: Resource, destFile: File, md: ModuleDescriptor) =
    delegate.toIvyFile(is, res, destFile, md)

  override def accept(res: Resource) = delegate.accept(res)
  override def getType() = delegate.getType()
  override def getMetadataArtifact(mrid: ModuleRevisionId, res: Resource) =
    delegate.getMetadataArtifact(mrid, res)
}
// @deprecated("We now use an Aether-based pom parser.", "0.13.8")
object CustomPomParser {

  // Evil hackery to override the default maven pom mappings.
  ReplaceMavenConfigurationMappings.init()

  /** The key prefix that indicates that this is used only to store extra information and is not intended for dependency resolution. */
  val InfoKeyPrefix = SbtPomExtraProperties.POM_INFO_KEY_PREFIX
  val ApiURLKey = SbtPomExtraProperties.POM_API_KEY
  val VersionSchemeKey = SbtPomExtraProperties.VERSION_SCHEME_KEY

  val SbtVersionKey = PomExtraDependencyAttributes.SbtVersionKey
  val ScalaVersionKey = PomExtraDependencyAttributes.ScalaVersionKey
  val ExtraAttributesKey = PomExtraDependencyAttributes.ExtraAttributesKey
  private[this] val unqualifiedKeys =
    Set(SbtVersionKey, ScalaVersionKey, ExtraAttributesKey, ApiURLKey, VersionSchemeKey)

  /**
   * In the new POM format of sbt plugins, the dependency to an sbt plugin
   * contains the sbt cross-version _2.12_1.0. The reason is we want Maven to be able
   * to resolve the dependency using the pattern:
   * <org>/<artifact-name>_2.12_1.0/<version>/<artifact-name>_2.12_1.0-<version>.pom
   * In sbt 1.x we use extra-attributes to resolve sbt plugins, so here we must remove
   * the sbt cross-version and keep the extra-attributes.
   * Parsing a dependency found in the new POM format produces the same module as
   * if it is found in the old POM format. It used not to contain the sbt cross-version
   * suffix, but that was invalid.
   * Hence we can resolve conflicts between new and old POM formats.
   *
   * To compare the two formats you can look at the POMs in:
   * https://repo1.maven.org/maven2/ch/epfl/scala/sbt-plugin-example-diamond_2.12_1.0/0.5.0/
   */
  private def removeSbtCrossVersion(
      properties: Map[String, String],
      moduleName: String
  ): String = {
    val sbtCrossVersion = for {
      sbtVersion <- properties.get(s"e:$SbtVersionKey")
      scalaVersion <- properties.get(s"e:$ScalaVersionKey")
    } yield s"_${scalaVersion}_$sbtVersion"
    sbtCrossVersion.map(moduleName.stripSuffix).getOrElse(moduleName)
  }

  // packagings that should be jars, but that Ivy doesn't handle as jars
  // TODO - move this elsewhere.
  val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar")
  val default = new CustomPomParser(PomModuleDescriptorParser.getInstance, defaultTransform)

  private[this] val TransformedHashKey = "e:sbtTransformHash"
  // A hash of the parameters transformation is based on.
  // If a descriptor has a different hash, we need to retransform it.
  private[this] def makeCoords(mrid: ModuleRevisionId): String =
    s"${mrid.getOrganisation}:${mrid.getName}:${mrid.getRevision}"

  // We now include the ModuleID in a hash, to ensure that parent-pom transformations don't corrupt child poms.
  private[this] def MakeTransformHash(md: ModuleDescriptor): String = {
    val coords: String = makeCoords(md.getModuleRevisionId)

    hash((unqualifiedKeys ++ JarPackagings ++ Set(coords)).toSeq.sorted)
  }

  private[this] def hash(ss: Seq[String]): String =
    Hash.toHex(Hash(ss.flatMap(_ getBytes "UTF-8").toArray))

  // Unfortunately, ModuleDescriptorParserRegistry is add-only and is a singleton instance.
  lazy val registerDefault: Unit = ModuleDescriptorParserRegistry.getInstance.addParser(default)

  def defaultTransform(parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
    if (transformedByThisVersion(md)) md
    else defaultTransformImpl(parser, md)

  private[this] def transformedByThisVersion(md: ModuleDescriptor): Boolean = {
    val oldTransformedHashKey = "sbtTransformHash"
    val extraInfo = md.getExtraInfo
    val MyHash = MakeTransformHash(md)
    // sbt 0.13.1 used "sbtTransformHash" instead of "e:sbtTransformHash" until #1192 so read both
    Option(extraInfo).isDefined &&
    ((Option(extraInfo get TransformedHashKey) orElse Option(
      extraInfo get oldTransformedHashKey
    )) match {
      case Some(MyHash) => true
      case _            => false
    })
  }

  private[this] def defaultTransformImpl(
      parser: ModuleDescriptorParser,
      md: ModuleDescriptor
  ): ModuleDescriptor = {
    val properties = getPomProperties(md)

    // Extracts extra attributes (currently, sbt and Scala versions) stored in the <properties> element of the pom.
    // These are attached to the module itself.
    val filtered = shouldBeUnqualified(properties)

    // Extracts extra attributes for the dependencies.
    // Because the <dependency> tag in pom.xml cannot include additional metadata,
    //  sbt includes extra attributes in a 'extraDependencyAttributes' property.
    // This is read/written from/to a pure string (no element structure) because Ivy only
    //  parses the immediate text nodes of the property.
    val extraDepAttributes = getDependencyExtra(filtered)

    val unqualify = toUnqualify(filtered)

    // Here we always add extra attributes.  There's a scenario where parent-pom information corrupts child-poms with "e:" namespaced xml elements
    // and we have to force the every generated xml file to have the appropriate xml namespace
    addExtra(unqualify, extraDepAttributes, parser, md)
  }
  // The <properties> element of the pom is used to store additional metadata, such as for sbt plugins or for the base URL for API docs.
  // This is done because the pom XSD does not appear to allow extra metadata anywhere else.
  // The extra sbt plugin metadata in pom.xml does not need to be readable by maven, but the other information may be.
  // However, the pom.xml needs to be valid in all cases because other tools like repository managers may read the pom.xml.
  private[sbt] def getPomProperties(md: ModuleDescriptor): Map[String, String] = {
    import scala.jdk.CollectionConverters._
    PomModuleDescriptorBuilder
      .extractPomProperties(md.getExtraInfo)
      .asInstanceOf[java.util.Map[String, String]]
      .asScala
      .toMap
  }
  private[sbt] def toUnqualify(propertyAttributes: Map[String, String]): Map[String, String] =
    (propertyAttributes - ExtraAttributesKey) map { case (k, v) => ("e:" + k, v) }

  private[this] def shouldBeUnqualified(m: Map[String, String]): Map[String, String] =
    m.view.filterKeys(unqualifiedKeys).toMap

  private[this] def addExtra(
      properties: Map[String, String],
      id: ModuleRevisionId
  ): ModuleRevisionId = {
    import scala.jdk.CollectionConverters._
    val oldExtra = qualifiedExtra(id)
    val newExtra = (oldExtra ++ properties).asJava
    // remove the sbt plugin cross version from the resolved ModuleRevisionId
    // sbt-plugin-example_2.12_1.0 => sbt-plugin-example
    val nameWithoutCrossVersion = removeSbtCrossVersion(properties, id.getName)
    ModuleRevisionId.newInstance(
      id.getOrganisation,
      nameWithoutCrossVersion,
      id.getBranch,
      id.getRevision,
      newExtra
    )
  }

  private[this] def getDependencyExtra(
      m: Map[String, String]
  ): Map[ModuleRevisionId, Map[String, String]] =
    PomExtraDependencyAttributes.getDependencyExtra(m)

  def qualifiedExtra(item: ExtendableItem): Map[String, String] =
    PomExtraDependencyAttributes.qualifiedExtra(item)
  def filterCustomExtra(item: ExtendableItem, include: Boolean): Map[String, String] =
    qualifiedExtra(item).view.filterKeys { k =>
      qualifiedIsExtra(k) == include
    }.toMap

  def writeDependencyExtra(s: Seq[DependencyDescriptor]): Seq[String] =
    PomExtraDependencyAttributes.writeDependencyExtra(s)

  // parses the sequence of dependencies with extra attribute information, with one dependency per line
  def readDependencyExtra(s: String): Seq[ModuleRevisionId] =
    PomExtraDependencyAttributes.readDependencyExtra(s)

  def qualifiedIsExtra(k: String): Boolean = PomExtraDependencyAttributes.qualifiedIsExtra(k)

  // Reduces the id to exclude custom extra attributes
  // This makes the id suitable as a key to associate a dependency parsed from a <dependency> element
  //  with the extra attributes from the <properties> section
  def simplify(id: ModuleRevisionId): ModuleRevisionId = PomExtraDependencyAttributes.simplify(id)

  private[this] def addExtra(
      dep: DependencyDescriptor,
      extra: Map[ModuleRevisionId, Map[String, String]]
  ): DependencyDescriptor = {
    val extras = if (extra.isEmpty) None else extra get simplify(dep.getDependencyRevisionId)
    extras match {
      case None             => dep
      case Some(extraAttrs) => transform(dep, revId => addExtra(extraAttrs, revId))
    }
  }
  private[this] def transform(
      dep: DependencyDescriptor,
      f: ModuleRevisionId => ModuleRevisionId
  ): DependencyDescriptor =
    DefaultDependencyDescriptor.transformInstance(
      dep,
      namespaceTransformer(dep.getDependencyRevisionId, f),
      false
    )

  private[this] def namespaceTransformer(
      txId: ModuleRevisionId,
      f: ModuleRevisionId => ModuleRevisionId
  ): NamespaceTransformer =
    new NamespaceTransformer {
      def transform(revId: ModuleRevisionId): ModuleRevisionId =
        if (revId == txId) f(revId) else revId
      def isIdentity = false
    }

  // TODO: It would be better if we can make dd.isForce to `false` when VersionRange.isVersionRange is `true`.
  private[this] def stripVersionRange(dd: DependencyDescriptor): DependencyDescriptor =
    VersionRange.stripMavenVersionRange(dd.getDependencyRevisionId.getRevision) match {
      case Some(newVersion) =>
        val id = dd.getDependencyRevisionId
        val newId = ModuleRevisionId.newInstance(
          id.getOrganisation,
          id.getName,
          id.getBranch,
          newVersion,
          id.getExtraAttributes
        )
        transform(dd, _ => newId)
      case None => dd
    }

  import scala.jdk.CollectionConverters._
  def addExtra(
      properties: Map[String, String],
      dependencyExtra: Map[ModuleRevisionId, Map[String, String]],
      parser: ModuleDescriptorParser,
      md: ModuleDescriptor
  ): ModuleDescriptor = {
    val dmd = new DefaultModuleDescriptor(parser, md.getResource)

    val mrid = addExtra(properties, md.getModuleRevisionId)
    val resolvedMrid = addExtra(properties, md.getResolvedModuleRevisionId)
    dmd.setModuleRevisionId(mrid)
    dmd.setResolvedModuleRevisionId(resolvedMrid)

    dmd.setDefault(md.isDefault)
    dmd.setHomePage(md.getHomePage)
    dmd.setDescription(md.getDescription)
    dmd.setLastModified(md.getLastModified)
    dmd.setStatus(md.getStatus())
    dmd.setPublicationDate(md.getPublicationDate())
    dmd.setResolvedPublicationDate(md.getResolvedPublicationDate())

    for (l <- md.getLicenses) dmd.addLicense(l)
    for ((key, value) <- md.getExtraInfo.asInstanceOf[java.util.Map[String, String]].asScala)
      dmd.addExtraInfo(key, value)
    dmd.addExtraInfo(
      TransformedHashKey,
      MakeTransformHash(md)
    ) // mark as transformed by this version, so we don't need to do it again
    for (
      (key, value) <- md.getExtraAttributesNamespaces
        .asInstanceOf[java.util.Map[String, String]]
        .asScala
    ) dmd.addExtraAttributeNamespace(key, value)
    IvySbt.addExtraNamespace(dmd)

    val withExtra = ArraySeq.unsafeWrapArray(md.getDependencies) map { dd =>
      addExtra(dd, dependencyExtra)
    }
    val withVersionRangeMod: Seq[DependencyDescriptor] =
      if (LMSysProp.modifyVersionRange) withExtra map { stripVersionRange }
      else withExtra
    val unique = IvySbt.mergeDuplicateDefinitions(withVersionRangeMod)
    unique foreach dmd.addDependency

    for (ed <- md.getInheritedDescriptors)
      dmd.addInheritedDescriptor(
        new DefaultExtendsDescriptor(md, ed.getLocation, ed.getExtendsTypes)
      )
    for (conf <- md.getConfigurations) {
      dmd.addConfiguration(conf)
      for (art <- md.getArtifacts(conf.getName)) {
        val ext = art.getExt
        val newExt = if (JarPackagings(ext)) "jar" else ext
        val nart = new DefaultArtifact(
          mrid,
          art.getPublicationDate,
          art.getName,
          art.getType,
          newExt,
          art.getUrl,
          art.getQualifiedExtraAttributes
        )
        dmd.addArtifact(conf.getName, nart)
      }
    }
    dmd
  }
}
