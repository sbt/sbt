/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import java.io.ByteArrayInputStream
import java.net.URL

import org.apache.ivy.{core, plugins}
import core.module.descriptor.{DefaultDependencyDescriptor, DefaultModuleDescriptor}
import core.settings.IvySettings
import plugins.parser.xml.XmlModuleDescriptorParser
import plugins.repository.Resource
import plugins.repository.url.URLResource

/** Subclasses the default Ivy file parser in order to provide access to protected methods.*/
private[sbt] object CustomXmlParser extends XmlModuleDescriptorParser with NotNull
{
	import XmlModuleDescriptorParser.Parser
	class CustomParser(settings: IvySettings, defaultConfig: Option[String]) extends Parser(CustomXmlParser, settings) with NotNull
	{
		if(defaultConfig.isDefined) setDefaultConfMapping("*->default(compile)")

		def setSource(url: URL) =
		{
			super.setResource(new URLResource(url))
			super.setInput(url)
		}
		def setInput(bytes: Array[Byte]) { setInput(new ByteArrayInputStream(bytes)) }
		/** Overridden because the super implementation overwrites the module descriptor.*/
		override def setResource(res: Resource) {}
		override def setMd(md: DefaultModuleDescriptor) = super.setMd(md)
		override def parseDepsConfs(confs: String, dd: DefaultDependencyDescriptor) = super.parseDepsConfs(confs, dd)
		override def getDefaultConf = defaultConfig.getOrElse(super.getDefaultConf)
	}
}