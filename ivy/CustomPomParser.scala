package sbt

	import org.apache.ivy.{core, plugins}
	import core.module.id.ModuleRevisionId
	import core.module.descriptor.{DefaultArtifact, DefaultExtendsDescriptor, DefaultModuleDescriptor, ModuleDescriptor}
	import plugins.parser.{m2, ModuleDescriptorParser, ModuleDescriptorParserRegistry, ParserSettings}
	import m2.{PomModuleDescriptorBuilder, PomModuleDescriptorParser}
	import plugins.repository.Resource

	import java.io.{File, InputStream}
	import java.net.URL

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

		// packagings that should be jars, but that Ivy doesn't handle as jars
	val JarPackagings = Set("eclipse-plugin")
	val default = new CustomPomParser(PomModuleDescriptorParser.getInstance, defaultTransform)

		// Unfortunately, ModuleDescriptorParserRegistry is add-only and is a singleton instance.
	lazy val registerDefault: Unit = ModuleDescriptorParserRegistry.getInstance.addParser(default)

	def defaultTransform(parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
	{
			import collection.JavaConverters._
		val properties = PomModuleDescriptorBuilder.extractPomProperties(md.getExtraInfo).asInstanceOf[java.util.Map[String,String]].asScala.toMap
		val filtered = shouldBeUnqualified(properties)
		val convertArtifacts = artifactExtIncorrect(md)
		if(filtered.isEmpty && !convertArtifacts) md else addExtra(filtered, parser, md)
	}
	private[this] def artifactExtIncorrect(md: ModuleDescriptor): Boolean =
		md.getConfigurations.exists(conf => md.getArtifacts(conf.getName).exists(art => JarPackagings(art.getExt)))
	private[this] def shouldBeUnqualified(m: Map[String, String]): Map[String, String] =
		m.filter { case (k,_) => k == SbtVersionKey || k == ScalaVersionKey }

	
	private[this] def condAddExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
		if(properties.isEmpty) id else addExtra(properties, id)
	private[this] def addExtra(properties: Map[String, String], id: ModuleRevisionId): ModuleRevisionId =
	{
			import collection.JavaConverters._
		val oldExtra = id.getQualifiedExtraAttributes.asInstanceOf[java.util.Map[String,String]].asScala
		val newExtra = (oldExtra ++ properties).asJava
		ModuleRevisionId.newInstance(id.getOrganisation, id.getName, id.getBranch, id.getRevision, newExtra)
	}

			import collection.JavaConverters._
	def addExtra(properties: Map[String, String], parser: ModuleDescriptorParser, md: ModuleDescriptor): ModuleDescriptor =
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
		for( dd <- md.getDependencies ) dmd.addDependency(dd)

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