/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

import verify.BasicTestSuite

object BuildServerConnectionSpec extends BasicTestSuite:

  test("parseSbtOpts should return empty vector for None"):
    val result = BuildServerConnection.parseSbtOpts(None)
    assert(result.isEmpty)

  test("parseSbtOpts should return empty vector for empty string"):
    val result = BuildServerConnection.parseSbtOpts(Some(""))
    assert(result.isEmpty)

  test("parseSbtOpts should parse -D system properties"):
    val result = BuildServerConnection.parseSbtOpts(Some("-Dsbt.boot.directory=/custom/path"))
    assert(result == Vector("-Dsbt.boot.directory=/custom/path"))

  test("parseSbtOpts should parse -X JVM options"):
    val result = BuildServerConnection.parseSbtOpts(Some("-Xmx2g -Xms512m"))
    assert(result == Vector("-Xmx2g", "-Xms512m"))

  test("parseSbtOpts should parse -J prefixed options and strip the prefix"):
    val result = BuildServerConnection.parseSbtOpts(Some("-J-Xmx4g"))
    assert(result == Vector("-Xmx4g"))

  test("parseSbtOpts should parse multiple mixed options"):
    val result = BuildServerConnection.parseSbtOpts(
      Some("-Dsbt.boot.directory=/path -Xmx2g -J-XX:+UseG1GC")
    )
    assert(result == Vector("-Dsbt.boot.directory=/path", "-Xmx2g", "-XX:+UseG1GC"))

  test("parseSbtOpts should filter out non-JVM options"):
    val result = BuildServerConnection.parseSbtOpts(Some("-Dfoo=bar --some-flag -Xmx1g"))
    assert(result == Vector("-Dfoo=bar", "-Xmx1g"))

  test("parseSbtOpts should handle whitespace-separated options"):
    val result = BuildServerConnection.parseSbtOpts(Some("  -Dfoo=bar   -Xmx1g  "))
    assert(result == Vector("-Dfoo=bar", "-Xmx1g"))
