package sbt

	import org.apache.ivy.{core, plugins, util}
	import core.module.id.ModuleRevisionId
	import core.module.descriptor.{DefaultArtifact, DefaultExtendsDescriptor, DefaultModuleDescriptor, ModuleDescriptor}
	import core.module.descriptor.{DefaultDependencyDescriptor, DependencyDescriptor}
	import plugins.parser.{m2, ModuleDescriptorParser, ModuleDescriptorParserRegistry, ParserSettings}
	import m2.{PomModuleDescriptorBuilder, PomModuleDescriptorParser}
	import plugins.repository.Resource
	import plugins.namespace.NamespaceTransformer
	import util.extendable.ExtendableItem

	import java.io.{File, InputStream}
	import java.net.URL
	import java.util.regex.Pattern

final class CustomPomParser(delegate: ModuleDescriptorParser, transform: (ModuleDescriptorParser, ModuleDescriptor) => ModuleDescriptor) extends ModuleDescriptorParser
{
	override def parseDescriptor(ivySettings: ParserSettings, descriptorURL: URL, validate: Boolean) =
		transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, validate))
		
	override def parseDescriptor(ivySettings: ParserSettings, descriptorURL: URL, res: Resource, validate: Boolean) =
		transform(this, delegate.parseDescriptor(ivySettings, descriptorURL, res, validate))
    
	override def toIvyFile(is: InputStream, res: Resource, destFile: File, md: ModuleDescriptor) = delegate.toIvyFile(is, res, destFile, md)

	override def accept(res: Resource) = delegate.accept(res)
	override def getType() = delegate.getType()
	override def getMetadataArtifact(mrid: ModuleRevisionId, res: Resource) = delegate.getMetadataArtifact(mrid, res)
}
object CustomPomParser
{
	val SbtVersionKey = "sbtVersion"
	val ScalaVersionKey = "scalaVersion"
	val ExtraAttributesKey = "extraDependencyAttributes"

		// packagings that should be jars, but that Ivy doesn't handle as jars
	val JarPackagings = Set("eclipse-plugin")
	val default = new CustomPomParser(PomModuleDescriptorParser.getInstance, defaultTransform)

		// Unfortunately, ModuleDescriptorParserRegistry is add-only and is a singleton instance.
	lazy val registerDefault: Unit = ModuleDescriptorParserRegistry.getInstance.addParser(default)

	def defaultTransform(parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
	{
		import collection.JavaConverters._
			// The <properties> element of the pom is used to store additional metadata for sbt plugins.
			// This is done because the pom XSD does not appear to allow extra metadata anywhere else.
			// The pom.xml does not need to be readable by maven because these are only enabled for sbt plugins.
			// However, the pom.xml needs to be valid because other tools, like repository managers may read the pom.xml.
		val properties = PomModuleDescriptorBuilder.extractPomProperties(md.getExtraInfo).asInstanceOf[java.util.Map[String,String]].asScala.toMap

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

		val unqualify = filtered - ExtraAttributesKey
		if(unqualify.isEmpty && extraDepAttributes.isEmpty && !convertArtifacts)
			md
		else
			addExtra(unqualify, extraDepAttributes, parser, md)
	}
	private[this] def artifactExtIncorrect(md: ModuleDescriptor): Boolean =
		md.getConfigurations.exists(conf => md.getArtifacts(conf.getName).exists(art => JarPackagings(art.getExt)))
	private[this] def shouldBeUnqualified(m: Map[String, String]): Map[String, String] =
		m.filter { case (SbtVersionKey | ScalaVersionKey | ExtraAttributesKey,_) => true; case _ => false }

	
	private[this] def condAddExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
		if(properties.isEmpty) id else addExtra(properties, id)
	private[this] def addExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
	{
			import collection.JavaConverters._
		val oldExtra = qualifiedExtra(id)
		val newExtra = (oldExtra ++ properties).asJava
		ModuleRevisionId.newInstance(id.getOrganisation, id.getName, id.getBranch, id.getRevision, newExtra)
	}

	private[this] def getDependencyExtra(m: Map[String, String]): Map[ModuleRevisionId, Map[String,String]] =
		(m get ExtraAttributesKey) match {
			case None => Map.empty
			case Some(str) =>
				def processDep(m: ModuleRevisionId) = (simplify(m), filterCustomExtra(m, include=true))
				readDependencyExtra(str).map(processDep).toMap
		}

	def qualifiedExtra(item: ExtendableItem): Map[String,String] =
	{
			import collection.JavaConverters._			
		item.getQualifiedExtraAttributes.asInstanceOf[java.util.Map[String,String]].asScala.toMap
	}
	def filterCustomExtra(item: ExtendableItem, include: Boolean): Map[String,String] =
		(qualifiedExtra(item) filterKeys { k => qualifiedIsExtra(k) == include })

	def writeDependencyExtra(s: Seq[DependencyDescriptor]): Seq[String] =
		s.flatMap { dd =>
			val revId = dd.getDependencyRevisionId
			if(filterCustomExtra(revId, include=true).isEmpty)
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
		ModuleRevisionId.newInstance(id.getOrganisation, id.getName, id.getBranch, id.getRevision, filterCustomExtra(id, include=false).asJava)
	}

	private[this] def addExtra(dep: DependencyDescriptor, extra: Map[ModuleRevisionId, Map[String, String]]): DependencyDescriptor =
	{
		val extras = if(extra.isEmpty) None else extra get simplify(dep.getDependencyRevisionId)
		extras match {
			case None => dep
			case Some(extraAttrs) => transform(dep, revId => addExtra(extraAttrs, revId))
		}
	}
	private[this] def transform(dep: DependencyDescriptor, f: ModuleRevisionId => ModuleRevisionId): DependencyDescriptor =
		DefaultDependencyDescriptor.transformInstance(dep, namespaceTransformer(dep.getDependencyRevisionId, f), false)
	private[this] def extraTransformer(txId: ModuleRevisionId, extra: Map[String, String]): NamespaceTransformer =
		namespaceTransformer(txId, revId => addExtra(extra, revId) )

	private[this] def namespaceTransformer(txId: ModuleRevisionId, f: ModuleRevisionId => ModuleRevisionId): NamespaceTransformer =
		new NamespaceTransformer {
			def transform(revId: ModuleRevisionId): ModuleRevisionId  =  if(revId == txId) f(revId) else revId
			def isIdentity = false
		}

			import collection.JavaConverters._
	def addExtra(properties: Map[String, String], dependencyExtra: Map[ModuleRevisionId, Map[String,String]], parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
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

		for(l <- md.getLicenses) dmd.addLicense(l)
		for( (key,value) <- md.getExtraInfo.asInstanceOf[java.util.Map[String,String]].asScala ) dmd.addExtraInfo(key, value)
		for( (key, value) <- md.getExtraAttributesNamespaces.asInstanceOf[java.util.Map[String,String]].asScala ) dmd.addExtraAttributeNamespace(key, value)
		for( dd <- md.getDependencies ) dmd.addDependency(addExtra(dd, dependencyExtra))

		for( ed <- md.getInheritedDescriptors) dmd.addInheritedDescriptor( new DefaultExtendsDescriptor( mrid, resolvedMrid, ed.getLocation, ed.getExtendsTypes) )
		for( conf <- md.getConfigurations) {
			dmd.addConfiguration(conf)
			for(art <- md.getArtifacts(conf.getName)) {
				val ext = art.getExt
				val newExt = if( JarPackagings(ext) ) "jar" else ext
				val nart = new DefaultArtifact(mrid, art.getPublicationDate, art.getName, art.getType, newExt, art.getUrl, art.getQualifiedExtraAttributes)
				dmd.addArtifact(conf.getName, nart)
			}
		}
		dmd
	}
}