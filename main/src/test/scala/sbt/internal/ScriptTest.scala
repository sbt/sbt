/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import verify.BasicTestSuite
import sbt.io.IO

object ScriptTest extends BasicTestSuite {

  test("stripShebang returns same lines when first line does not start with #!") {
    val lines = Seq("println(1)", "val x = 2")
    val result = Script.stripShebang(lines)
    assert(result == lines)
  }

  test("stripShebang drops first line when it is a shebang") {
    val lines = Seq("#!/usr/bin/env sbt -Dsbt.main.class=sbt.ScriptMain", "println(1)")
    val result = Script.stripShebang(lines)
    assert(result == Seq("println(1)"))
  }

  test("stripShebang leaves empty list unchanged") {
    val result = Script.stripShebang(Seq.empty)
    assert(result.isEmpty)
  }

  test("stripShebang leaves single non-shebang line unchanged") {
    val lines = Seq("println(42)")
    val result = Script.stripShebang(lines)
    assert(result == lines)
  }

  test("stripShebang drops only first line when it is #!") {
    val lines = Seq("#!", "*/", "println(1)")
    val result = Script.stripShebang(lines)
    assert(result == Seq("*/", "println(1)"))
  }

  test("scriptBodyLines returns all lines when file has no blocks") {
    val f = File.createTempFile("script", ".scala")
    try {
      IO.write(f, "println(1)\nval x = 2\n")
      val result = Script.scriptBodyLines(f)
      assert(result.contains("println(1)"))
      assert(result.contains("val x = 2"))
      assert(!result.exists(_.startsWith("/***")))
    } finally f.delete()
  }

  test("scriptBodyLines excludes lines inside /*** */ block") {
    val f = File.createTempFile("script", ".scala")
    try {
      IO.write(
        f,
        """println("before")
          |/***
          |scalaVersion := "3.0.0"
          |*/
          |println("after")
          |""".stripMargin
      )
      val result = Script.scriptBodyLines(f)
      assert(result.contains("println(\"before\")"))
      assert(result.contains("println(\"after\")"))
      assert(!result.contains("scalaVersion := \"3.0.0\""))
      assert(
        !result.contains("*/"),
        "closing */ must not appear in script body (would break wrapped Main.scala)"
      )
    } finally f.delete()
  }

  test("scriptBodyLines excludes block content when file has only a block") {
    val f = File.createTempFile("script", ".scala")
    try {
      IO.write(
        f,
        """/***
          |scalaVersion := "3.0.0"
          |*/
          |""".stripMargin
      )
      val result = Script.scriptBodyLines(f)
      assert(
        !result.contains("scalaVersion := \"3.0.0\""),
        s"block content must be excluded, got $result"
      )
    } finally f.delete()
  }

  test("blocks parses block containing settings") {
    val f = File.createTempFile("script", ".scala")
    try {
      IO.write(
        f,
        """line0
          |/***
          |scalaVersion := "3.0.0"
          |*/
          |line3
          |""".stripMargin
      )
      val result = Script.blocks(f)
      val settingBlock = result.find(_.lines.contains("scalaVersion := \"3.0.0\""))
      assert(settingBlock.isDefined, s"expected a block with scalaVersion, got $result")
    } finally f.delete()
  }
}
