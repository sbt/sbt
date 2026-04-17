/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.util.Logger

object TemplateCommandUtilTest extends verify.BasicTestSuite:

  private val localTemplateSlugs = List(
    "scala/toolkit.local",
    "typelevel/toolkit.local",
    "sbt/cross-platform.local"
  )

  test("defaultTemplateDescriptions includes all built-in local template slugs"):
    val slugs = TemplateCommandUtil.defaultTemplateDescriptions.map(_._1)
    for slug <- localTemplateSlugs do
      assert(slugs.contains(slug), s"defaultTemplateDescriptions should contain '$slug'")

  test("defaultRunLocalTemplate throws for unknown .local slug"):
    val log = Logger.Null
    val ex =
      try {
        TemplateCommandUtil.defaultRunLocalTemplate(List("unknown/template.local"), log)
        null
      } catch { case e: IllegalArgumentException => e }
    assert(ex ne null)
    assert(ex.getMessage.contains("Local template not found for:"))
    assert(ex.getMessage.contains("unknown/template.local"))

  test("normalizeTemplateArgs rewrites ssh url with branch option"):
    val args = List(
      "ssh://git@github.com/scala/scala-seed.g8.git",
      "--branch",
      "2.12.x"
    )
    val normalized = TemplateCommandUtil.normalizeTemplateArgs(args)
    assert(
      normalized == List("ssh://git@github.com/scala/scala-seed.g8.git#2.12.x"),
      s"unexpected normalized args: $normalized"
    )

  test("normalizeTemplateArgs keeps non-ssh url unchanged"):
    val args = List(
      "https://github.com/scala/scala-seed.g8.git",
      "--branch",
      "2.12.x"
    )
    val normalized = TemplateCommandUtil.normalizeTemplateArgs(args)
    assert(normalized == args, s"unexpected normalized args: $normalized")

  test("normalizeTemplateArgs keeps ssh url unchanged when fragment already exists"):
    val args = List(
      "ssh://git@github.com/scala/scala-seed.g8.git#main",
      "--branch",
      "2.12.x"
    )
    val normalized = TemplateCommandUtil.normalizeTemplateArgs(args)
    assert(normalized == args, s"unexpected normalized args: $normalized")

  test("normalizeTemplateArgs does not rewrite when branch value is missing"):
    val args = List("ssh://git@github.com/scala/scala-seed.g8.git", "--branch")
    val normalized = TemplateCommandUtil.normalizeTemplateArgs(args)
    assert(normalized == args, s"unexpected normalized args: $normalized")
