/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.specs2.Specification

class ProjectSpec extends Specification {
  def is = s2"""

  This is a specification to check utility methods on the Project object

  Project should
    normalize projectIDs if they are empty  ${normalizeEmptyFileName}

  """

  def emptyFilename = ""

  def normalizeEmptyFileName =
    Project.normalizeProjectID(emptyFilename) must equalTo(Right("root"))
}
