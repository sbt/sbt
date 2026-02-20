/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.io.File
import BuildPaths.{ expandTildePrefix, defaultGlobalBase, GlobalBaseProperty }

object BuildPathsTest extends verify.BasicTestSuite:

  test("defaultGlobalBase respects sbt.global.base system property"):
    val custom = new File(System.getProperty("java.io.tmpdir"), "sbt-test-3681").getAbsolutePath
    val prev = sys.props.get(GlobalBaseProperty)
    try
      sys.props(GlobalBaseProperty) = custom
      assert(defaultGlobalBase.getAbsolutePath == new File(custom).getAbsolutePath)
    finally
      prev match
        case Some(v) => sys.props(GlobalBaseProperty) = v
        case None    => sys.props.remove(GlobalBaseProperty)

  test("defaultGlobalBase returns absolute path"):
    assert(defaultGlobalBase.getAbsolutePath.nonEmpty)
    assert(defaultGlobalBase.isAbsolute)

  test("expandTildePrefix should expand empty path to itself"):
    assertEquals("", expandTildePrefix(""))

  test("it should expand /home/user/path to itself"):
    assertEquals("/home/user/path", expandTildePrefix("/home/user/path"))

  test("it should expand /~/foo/ to itself"):
    assertEquals("/~/foo/", expandTildePrefix("/~/foo/"))

  test("it should expand ~ to $HOME"):
    assertEquals(sys.env.getOrElse("HOME", ""), expandTildePrefix("~"))

  test("it should expand ~/foo/bar to $HOME/foo/bar"):
    assertEquals(sys.env.getOrElse("HOME", "") + "/foo/bar", expandTildePrefix("~/foo/bar"))

  test("it should expand ~+ to $PWD"):
    assertEquals(sys.env.getOrElse("PWD", ""), expandTildePrefix("~+"))

  test("it should expand ~+/foo/bar to $PWD/foo/bar"):
    assertEquals(sys.env.getOrElse("PWD", "") + "/foo/bar", expandTildePrefix("~+/foo/bar"))

  test("it should expand ~- to $OLDPWD"):
    assertEquals(sys.env.getOrElse("OLDPWD", ""), expandTildePrefix("~-"))

  test("it should expand ~-/foo/bar to $OLDPWD/foo/bar"):
    assertEquals(sys.env.getOrElse("OLDPWD", "") + "/foo/bar", expandTildePrefix("~-/foo/bar"))
end BuildPathsTest
