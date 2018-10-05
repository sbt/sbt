/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.specs2.mutable.Specification

object BuildPathsTest extends Specification {

  private def assertExpandedPath(given: String, expected: String) = {
    val actual = BuildPaths.expandTildePrefix(given)

    actual must be equalTo (expected)
  }

  "expandTildePrefix" should {

    "expand empty path to itself" in {
      assertExpandedPath("", "")
    }

    "expand /home/user/path to itself" in {
      assertExpandedPath("/home/user/path", "/home/user/path")
    }

    "expand /~/foo/ to itself" in {
      assertExpandedPath("/~/foo/", "/~/foo/")
    }

    "expand ~ to $HOME" in {
      assertExpandedPath("~", sys.env.getOrElse("HOME", ""))
    }

    "expand ~/foo/bar to $HOME/foo/bar" in {
      assertExpandedPath("~/foo/bar", sys.env.getOrElse("HOME", "") + "/foo/bar")
    }

    "expand ~+ to $PWD" in {
      assertExpandedPath("~+", sys.env.getOrElse("PWD", ""))
    }

    "expand ~+/foo/bar to $PWD/foo/bar" in {
      assertExpandedPath("~+/foo/bar", sys.env.getOrElse("PWD", "") + "/foo/bar")
    }

    "expand ~- to $OLDPWD" in {
      assertExpandedPath("~-", sys.env.getOrElse("OLDPWD", ""))
    }

    "expand ~-/foo/bar to $OLDPWD/foo/bar" in {
      assertExpandedPath("~-/foo/bar", sys.env.getOrElse("OLDPWD", "") + "/foo/bar")
    }

  }

}
