/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.io.File

final case class Exit(code: Int) extends xsbti.Exit {
  require(code >= 0)
}
final case class Reboot(scalaVersion: String,
                        argsList: Seq[String],
                        app: xsbti.ApplicationID,
                        baseDirectory: File)
    extends xsbti.Reboot {
  def arguments = argsList.toArray
}
final case class ApplicationID(groupID: String,
                               name: String,
                               version: String,
                               mainClass: String,
                               components: Seq[String],
                               crossVersionedValue: xsbti.CrossValue,
                               extra: Seq[File])
    extends xsbti.ApplicationID {
  def mainComponents = components.toArray
  def classpathExtra = extra.toArray
  def crossVersioned = crossVersionedValue != xsbti.CrossValue.Disabled
}
object ApplicationID {
  def apply(delegate: xsbti.ApplicationID, newVersion: String): ApplicationID =
    apply(delegate).copy(version = newVersion)
  def apply(delegate: xsbti.ApplicationID): ApplicationID =
    ApplicationID(delegate.groupID,
                  delegate.name,
                  delegate.version,
                  delegate.mainClass,
                  delegate.mainComponents,
                  delegate.crossVersionedValue,
                  delegate.classpathExtra)
}
