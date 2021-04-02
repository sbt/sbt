/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

object ProjectSpec extends verify.BasicTestSuite {
  test("Project should normalize projectIDs if they are empty") {
    assert(Project.normalizeProjectID(emptyFilename) == Right("root"))
  }

  def emptyFilename = ""
}
