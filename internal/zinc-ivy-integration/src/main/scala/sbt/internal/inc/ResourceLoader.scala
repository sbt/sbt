package sbt.internal.inc

import java.util.Properties

/** Defines utilities to load Java properties from the JVM. */
private[inc] object ResourceLoader {
  def getPropertiesFor(resource: String, classLoader: ClassLoader): Properties = {
    val properties = new java.util.Properties
    val propertiesStream = getClass.getResource(resource).openStream
    try { properties.load(propertiesStream) } finally { propertiesStream.close() }
    properties
  }
}
