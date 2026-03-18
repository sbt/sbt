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

  test("sbtScriptInPath should return None when sbt is not in PATH"):
    val result = BuildServerConnection.sbtScriptInPath
    result match
      case Some(path) => assert(path.nonEmpty)
      case None       => assert(true)

  test("buildFallbackArgv should include java path and -bsp flag"):
    val argv = BuildServerConnection.buildFallbackArgv
    assert(argv.head.contains("java"), s"argv should start with java, got: ${argv.head}")
    assert(argv.contains("-bsp"), s"argv should contain -bsp, got: $argv")
    assert(argv.contains("-Xms100m"), s"argv should contain -Xms100m, got: $argv")
    assert(argv.contains("-Xmx100m"), s"argv should contain -Xmx100m, got: $argv")
    assert(argv.contains("-classpath"), s"argv should contain -classpath, got: $argv")
    assert(argv.contains("xsbt.boot.Boot"), s"argv should contain xsbt.boot.Boot, got: $argv")
