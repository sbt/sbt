/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package scripted

import java.{ util as ju }
import java.net.URL

final class FilteredLoader(parent: ClassLoader) extends ClassLoader(parent) {
  @throws(classOf[ClassNotFoundException])
  override final def loadClass(className: String, resolve: Boolean): Class[?] = {
    if (className.startsWith("java.") || className.startsWith("javax."))
      super.loadClass(className, resolve)
    else
      throw new ClassNotFoundException(className)
  }
  override def getResources(name: String): ju.Enumeration[URL] = null
  override def getResource(name: String): URL = null
}
