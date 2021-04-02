/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.util.Properties

/** Defines utilities to load Java properties from the JVM. */
private[inc] object ResourceLoader {
  def getPropertiesFor(resource: String): Properties = {
    val properties = new Properties
    val propertiesStream = getClass.getResource(resource).openStream
    try {
      properties.load(propertiesStream)
    } finally propertiesStream.close()
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
