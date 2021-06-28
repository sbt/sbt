/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import BuildPaths.expandTildePrefix

object BuildPathsTest extends verify.BasicTestSuite {

  test("expandTildePrefix should expand empty path to itself") {
    assertEquals("", expandTildePrefix(""))
  }

  test("it should expand /home/user/path to itself") {
    assertEquals("/home/user/path", expandTildePrefix("/home/user/path"))
  }

  test("it should expand /~/foo/ to itself") {
    assertEquals("/~/foo/", expandTildePrefix("/~/foo/"))
  }

  test("it should expand ~ to $HOME") {
    assertEquals(sys.env.getOrElse("HOME", ""), expandTildePrefix("~"))
  }

  test("it should expand ~/foo/bar to $HOME/foo/bar") {
    assertEquals(sys.env.getOrElse("HOME", "") + "/foo/bar", expandTildePrefix("~/foo/bar"))
  }

  test("it should expand ~+ to $PWD") {
    assertEquals(sys.env.getOrElse("PWD", ""), expandTildePrefix("~+"))
  }

  test("it should expand ~+/foo/bar to $PWD/foo/bar") {
    assertEquals(sys.env.getOrElse("PWD", "") + "/foo/bar", expandTildePrefix("~+/foo/bar"))
  }

  test("it should expand ~- to $OLDPWD") {
    assertEquals(sys.env.getOrElse("OLDPWD", ""), expandTildePrefix("~-"))
  }

  test("it should expand ~-/foo/bar to $OLDPWD/foo/bar") {
    assertEquals(sys.env.getOrElse("OLDPWD", "") + "/foo/bar", expandTildePrefix("~-/foo/bar"))
  }
}
