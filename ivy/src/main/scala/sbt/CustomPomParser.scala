package sbt

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.module.descriptor.{ DefaultArtifact, DefaultExtendsDescriptor, DefaultModuleDescriptor, ModuleDescriptor }
import org.apache.ivy.core.module.descriptor.{ DefaultDependencyDescriptor, DependencyDescriptor }
import org.apache.ivy.plugins.parser.{ ModuleDescriptorParser, ModuleDescriptorParserRegistry, ParserSettings }
import org.apache.ivy.plugins.parser.m2.{ ReplaceMavenConfigurationMappings, PomModuleDescriptorBuilder, PomModuleDescriptorParser }
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.namespace.NamespaceTransformer
import org.apache.ivy.util.extendable.ExtendableItem

import java.io.{ File, InputStream }
import java.net.URL
import java.util.regex.Pattern

@deprecated("0.13.8", "We now use an Aether-based pom parser.")
final class CustomPomParser(delegate: ModuleDescriptorParser, transform: (ModuleDescriptorParser, ModuleDescriptor) => ModuleDescriptor) extends ModuleDescriptorParser {
  override def parseDescriptor(ivySettings: ParserSettings, descriptorURL: URL, validate: Boolean) =
    transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, validate))

  override def parseDescriptor(ivySettings: ParserSettings, descriptorURL: URL, res: Resource, validate: Boolean) =
    transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, res, validate))

  override def toIvyFile(is: InputStream, res: Resource, destFile: File, md: ModuleDescriptor) = delegate.toIvyFile(is, res, destFile, md)

  override def accept(res: Resource) = delegate.accept(res)
  override def getType() = delegate.getType()
  override def getMetadataArtifact(mrid: ModuleRevisionId, res: Resource) = delegate.getMetadataArtifact(mrid, res)
}
@deprecated("0.13.8", "We now use an Aether-based pom parser.")
object CustomPomParser {

  // Evil hackery to override the default maven pom mappings.
  ReplaceMavenConfigurationMappings.init()

  /** The key prefix that indicates that this is used only to store extra information and is not intended for dependency resolution.*/
  val InfoKeyPrefix = "info."
  val ApiURLKey = "info.apiURL"

  val SbtVersionKey = "sbtVersion"
  val ScalaVersionKey = "scalaVersion"
  val ExtraAttributesKey = "extraDependencyAttributes"
  private[this] val unqualifiedKeys = Set(SbtVersionKey, ScalaVersionKey, ExtraAttributesKey, ApiURLKey)

  // packagings that should be jars, but that Ivy doesn't handle as jars
  val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar")
  val default = new CustomPomParser(PomModuleDescriptorParser.getInstance, defaultTransform)

  private[this] val TransformedHashKey = "e:sbtTransformHash"
  // A hash of the parameters transformation is based on.
  // If a descriptor has a different hash, we need to retransform it.
  private[this] val TransformHash: String = hash((unqualifiedKeys ++ JarPackagings).toSeq.sorted)
  private[this] def hash(ss: Seq[String]): String = Hash.toHex(Hash(ss.flatMap(_ getBytes "UTF-8").toArray))

  // Unfortunately, ModuleDescriptorParserRegistry is add-only and is a singleton instance.
  lazy val registerDefault: Unit = ModuleDescriptorParserRegistry.getInstance.addParser(default)

  def defaultTransform(parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
    if (transformedByThisVersion(md)) md
    else defaultTransformImpl(parser, md)

  private[this] def transformedByThisVersion(md: ModuleDescriptor): Boolean =
    {
      val oldTransformedHashKey = "sbtTransformHash"
      val extraInfo = md.getExtraInfo
      // sbt 0.13.1 used "sbtTransformHash" instead of "e:sbtTransformHash" until #1192 so read both 
      Option(extraInfo).isDefined &&
        ((Option(extraInfo get TransformedHashKey) orElse Option(extraInfo get oldTransformedHashKey)) match {
          case Some(TransformHash) => true
          case _                   => false
        })
    }

  private[this] def defaultTransformImpl(parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
    {
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

      // Fixes up the detected extension in some cases missed by Ivy.
      val convertArtifacts = artifactExtIncorrect(md)

      // Merges artifact sections for duplicate dependency definitions
      val mergeDuplicates = IvySbt.hasDuplicateDependencies(md.getDependencies)

      val unqualify = toUnqualify(filtered)
      if (unqualify.isEmpty && extraDepAttributes.isEmpty && !convertArtifacts && !mergeDuplicates)
        md
      else
        addExtra(unqualify, extraDepAttributes, parser, md)
    }
  // The <properties> element of the pom is used to store additional metadata, such as for sbt plugins or for the base URL for API docs.
  // This is done because the pom XSD does not appear to allow extra metadata anywhere else.
  // The extra sbt plugin metadata in pom.xml does not need to be readable by maven, but the other information may be.
  // However, the pom.xml needs to be valid in all cases because other tools like repository managers may read the pom.xml.
  private[sbt] def getPomProperties(md: ModuleDescriptor): Map[String, String] =
    {
      import collection.JavaConverters._
      PomModuleDescriptorBuilder.extractPomProperties(md.getExtraInfo).asInstanceOf[java.util.Map[String, String]].asScala.toMap
    }
  private[sbt] def toUnqualify(propertyAttributes: Map[String, String]): Map[String, String] =
    (propertyAttributes - ExtraAttributesKey) map { case (k, v) => ("e:" + k, v) }

  private[this] def artifactExtIncorrect(md: ModuleDescriptor): Boolean =
    md.getConfigurations.exists(conf => md.getArtifacts(conf.getName).exists(art => JarPackagings(art.getExt)))
  private[this] def shouldBeUnqualified(m: Map[String, String]): Map[String, String] = m.filterKeys(unqualifiedKeys)

  private[this] def condAddExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
    if (properties.isEmpty) id else addExtra(properties, id)
  private[this] def addExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
    {
      import collection.JavaConverters._
      val oldExtra = qualifiedExtra(id)
      val newExtra = (oldExtra ++ properties).asJava
      ModuleRevisionId.newInstance(id.getOrganisation, id.getName, id.getBranch, id.getRevision, newExtra)
    }

  private[this] def getDependencyExtra(m: Map[String, String]): Map[ModuleRevisionId, Map[String, String]] =
    (m get ExtraAttributesKey) match {
      case None => Map.empty
      case Some(str) =>
        def processDep(m: ModuleRevisionId) = (simplify(m), filterCustomExtra(m, include = true))
        readDependencyExtra(str).map(processDep).toMap
    }

  def qualifiedExtra(item: ExtendableItem): Map[String, String] =
    {
      import collection.JavaConverters._
      item.getQualifiedExtraAttributes.asInstanceOf[java.util.Map[String, String]].asScala.toMap
    }
  def filterCustomExtra(item: ExtendableItem, include: Boolean): Map[String, String] =
    (qualifiedExtra(item) filterKeys { k => qualifiedIsExtra(k) == include })

  def writeDependencyExtra(s: Seq[DependencyDescriptor]): Seq[String] =
    s.flatMap { dd =>
      val revId = dd.getDependencyRevisionId
      if (filterCustomExtra(revId, include = true).isEmpty)
        Nil
      else
        revId.encodeToString :: Nil
    }

  // parses the sequence of dependencies with extra attribute information, with one dependency per line
  def readDependencyExtra(s: String): Seq[ModuleRevisionId] =
    LinesP.split(s).map(_.trim).filter(!_.isEmpty).map(ModuleRevisionId.decode)

  private[this] val LinesP = Pattern.compile("(?m)^")

  def qualifiedIsExtra(k: String): Boolean = k.endsWith(ScalaVersionKey) || k.endsWith(SbtVersionKey)

  // Reduces the id to exclude custom extra attributes
  // This makes the id suitable as a key to associate a dependency parsed from a <dependency> element
  //  with the extra attributes from the <properties> section
  def simplify(id: ModuleRevisionId): ModuleRevisionId =
    {
      import collection.JavaConverters._
      ModuleRevisionId.newInstance(id.getOrganisation, id.getName, id.getBranch, id.getRevision, filterCustomExtra(id, include = false).asJava)
    }

  private[this] def addExtra(dep: DependencyDescriptor, extra: Map[ModuleRevisionId, Map[String, String]]): DependencyDescriptor =
    {
      val extras = if (extra.isEmpty) None else extra get simplify(dep.getDependencyRevisionId)
      extras match {
        case None             => dep
        case Some(extraAttrs) => transform(dep, revId => addExtra(extraAttrs, revId))
      }
    }
  private[this] def transform(dep: DependencyDescriptor, f: ModuleRevisionId => ModuleRevisionId): DependencyDescriptor =
    DefaultDependencyDescriptor.transformInstance(dep, namespaceTransformer(dep.getDependencyRevisionId, f), false)
  private[this] def extraTransformer(txId: ModuleRevisionId, extra: Map[String, String]): NamespaceTransformer =
    namespaceTransformer(txId, revId => addExtra(extra, revId))

  private[this] def namespaceTransformer(txId: ModuleRevisionId, f: ModuleRevisionId => ModuleRevisionId): NamespaceTransformer =
    new NamespaceTransformer {
      def transform(revId: ModuleRevisionId): ModuleRevisionId = if (revId == txId) f(revId) else revId
      def isIdentity = false
    }

  import collection.JavaConverters._
  def addExtra(properties: Map[String, String], dependencyExtra: Map[ModuleRevisionId, Map[String, String]], parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
    {
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
      for ((key, value) <- md.getExtraInfo.asInstanceOf[java.util.Map[String, String]].asScala) dmd.addExtraInfo(key, value)
      dmd.addExtraInfo(TransformedHashKey, TransformHash) // mark as transformed by this version, so we don't need to do it again
      for ((key, value) <- md.getExtraAttributesNamespaces.asInstanceOf[java.util.Map[String, String]].asScala) dmd.addExtraAttributeNamespace(key, value)
      IvySbt.addExtraNamespace(dmd)

      val withExtra = md.getDependencies map { dd => addExtra(dd, dependencyExtra) }
      val unique = IvySbt.mergeDuplicateDefinitions(withExtra)
      unique foreach dmd.addDependency

      for (ed <- md.getInheritedDescriptors) dmd.addInheritedDescriptor(new DefaultExtendsDescriptor(md, ed.getLocation, ed.getExtendsTypes))
      for (conf <- md.getConfigurations) {
        dmd.addConfiguration(conf)
        for (art <- md.getArtifacts(conf.getName)) {
          val ext = art.getExt
          val newExt = if (JarPackagings(ext)) "jar" else ext
          val nart = new DefaultArtifact(mrid, art.getPublicationDate, art.getName, art.getType, newExt, art.getUrl, art.getQualifiedExtraAttributes)
          dmd.addArtifact(conf.getName, nart)
        }
      }
      dmd
    }
}