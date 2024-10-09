/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt.internal.librarymanagement

import java.io.ByteArrayInputStream
import java.net.URL

import org.apache.ivy.core.module.descriptor.{
  DefaultDependencyDescriptor,
  DefaultModuleDescriptor
}
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLResource

/** Subclasses the default Ivy file parser in order to provide access to protected methods. */
private[sbt] object CustomXmlParser extends XmlModuleDescriptorParser {
  import XmlModuleDescriptorParser.Parser
  class CustomParser(settings: IvySettings, defaultConfig: Option[String])
      extends Parser(CustomXmlParser, settings) {
    def setSource(url: URL) = {
      super.setResource(new URLResource(url))
      super.setInput(url)
    }
    def setInput(bytes: Array[Byte]): Unit = setInput(new ByteArrayInputStream(bytes))

    /** Overridden because the super implementation overwrites the module descriptor. */
    override def setResource(res: Resource): Unit = ()
    override def setMd(md: DefaultModuleDescriptor) = {
      super.setMd(md)
      if (defaultConfig.isDefined) setDefaultConfMapping("*->default(compile)")
    }
    override def parseDepsConfs(confs: String, dd: DefaultDependencyDescriptor) =
      super.parseDepsConfs(confs, dd)
    override def getDefaultConf = defaultConfig.getOrElse(super.getDefaultConf)
  }
}
