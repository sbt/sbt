/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.librarymanagement.DependencyResolution
import sbt.librarymanagement.ivy.IvyDependencyResolution
import sbt.Keys.{ csrConfiguration, ivyConfiguration, useCoursier }
import lmcoursier.CoursierDependencyResolution

private[sbt] object DependencyResolutionTask {
  val get: Def.Initialize[Task[DependencyResolution]] = Def.taskIf {
    if (useCoursier.value) CoursierDependencyResolution(csrConfiguration.value)
    else IvyDependencyResolution(ivyConfiguration.value, CustomHttp.okhttpClient.value)
  }
}
