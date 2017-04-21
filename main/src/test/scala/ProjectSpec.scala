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
