/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.util.Properties
import scala.util.Using

/** Defines utilities to load Java properties from the JVM. */
private[inc] object ResourceLoader {
  def getPropertiesFor(resource: String): Properties = {
    val properties = new Properties
    val resourceUrl = getClass.getResource(resource)
    if (resourceUrl eq null) {
      throw new java.io.FileNotFoundException(s"Resource not found: $resource")
    }
    Using.resource(resourceUrl.openStream) { propertiesStream =>
      properties.load(propertiesStream)
    }
    properties
  }

  def getSafePropertiesFor(resource: String, classLoader: ClassLoader): Properties = {
    val properties = new Properties
    val propertiesStream = classLoader.getResourceAsStream(resource)
    if (propertiesStream ne null) {
      try {
        properties.load(propertiesStream)
      } catch {
        case _: Exception =>
      } finally propertiesStream.close()
    }
    properties
  }
}
