/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.Keys.{ artifactName, crossTarget, projectID, scalaBinaryVersion, scalaVersion }
import sbt.librarymanagement.{ Artifact, ScalaVersion }
import sbt.io.syntax._

private[sbt] object ArtifactPathSetting {
  def apply(art: SettingKey[Artifact]) = Def.setting {
    val f = artifactName.value
    crossTarget.value / f(
      ScalaVersion(
        (scalaVersion in artifactName).value,
        (scalaBinaryVersion in artifactName).value
      ),
      projectID.value,
      art.value
    )
  }
}
