/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

final class FilteredLoader(parent: ClassLoader) extends ClassLoader(parent) {
  @throws(classOf[ClassNotFoundException])
  override final def loadClass(className: String, resolve: Boolean): Class[_] = {
    if (className.startsWith("java.") || className.startsWith("javax."))
      super.loadClass(className, resolve)
    else
      throw new ClassNotFoundException(className)
  }
  override def getResources(name: String) = null
  override def getResource(name: String) = null
}
