/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

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
