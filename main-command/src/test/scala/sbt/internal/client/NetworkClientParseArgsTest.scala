/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.client

import verify.BasicTestSuite

object NetworkClientParseArgsTest extends BasicTestSuite:

  private def parse(args: String*): NetworkClient.Arguments =
    NetworkClient.parseArgs(args.toArray)

  // -- Value-taking launcher flags --

  test("-mem 10000 compile drops -mem and its value"):
    val result = parse("-mem", "10000", "compile")
    assert(!result.sbtArguments.contains("-mem"))
    assert(!result.sbtArguments.contains("10000"))
    assert(!result.commandArguments.contains("-mem"))
    assert(!result.commandArguments.contains("10000"))
    assert(result.commandArguments.contains("compile"))

  test("--mem 10000 compile drops --mem and its value"):
    val result = parse("--mem", "10000", "compile")
    assert(!result.sbtArguments.contains("--mem"))
    assert(!result.sbtArguments.contains("10000"))
    assert(result.commandArguments.contains("compile"))

  test("-jvm-debug 5005 compile drops both flag and port"):
    val result = parse("-jvm-debug", "5005", "compile")
    assert(!result.sbtArguments.contains("-jvm-debug"))
    assert(!result.sbtArguments.contains("5005"))
    assert(!result.commandArguments.contains("-jvm-debug"))
    assert(!result.commandArguments.contains("5005"))
    assert(result.commandArguments.contains("compile"))

  test("-java-home /path/to/jdk compile drops both"):
    val result = parse("-java-home", "/path/to/jdk", "compile")
    assert(!result.sbtArguments.contains("-java-home"))
    assert(!result.sbtArguments.contains("/path/to/jdk"))
    assert(!result.commandArguments.contains("-java-home"))
    assert(!result.commandArguments.contains("/path/to/jdk"))
    assert(result.commandArguments.contains("compile"))

  test("-mem at end of args with no value does not crash"):
    val result = parse("-mem")
    assert(!result.sbtArguments.contains("-mem"))
    assert(result.commandArguments.isEmpty)

  // -- No-value launcher flags --

  test("--client compile drops --client"):
    val result = parse("--client", "compile")
    assert(!result.sbtArguments.contains("--client"))
    assert(!result.commandArguments.contains("--client"))
    assert(result.commandArguments.contains("compile"))

  test("-client compile drops -client"):
    val result = parse("-client", "compile")
    assert(!result.sbtArguments.contains("-client"))
    assert(!result.commandArguments.contains("-client"))
    assert(result.commandArguments.contains("compile"))

  test("-debug is dropped"):
    val result = parse("-debug", "compile")
    assert(!result.sbtArguments.contains("-debug"))
    assert(!result.commandArguments.contains("-debug"))
    assert(result.commandArguments.contains("compile"))

  test("-batch is dropped"):
    val result = parse("-batch", "compile")
    assert(!result.sbtArguments.contains("-batch"))
    assert(result.commandArguments.contains("compile"))

  test("-allow-empty is dropped"):
    val result = parse("-allow-empty", "compile")
    assert(!result.sbtArguments.contains("-allow-empty"))
    assert(!result.commandArguments.contains("-allow-empty"))
    assert(result.commandArguments.contains("compile"))

  // -- Eq-syntax flags and -J* --

  test("--supershell=false is dropped"):
    val result = parse("--supershell=false", "compile")
    assert(!result.sbtArguments.exists(_.contains("supershell")))
    assert(result.commandArguments.contains("compile"))

  test("--color=never is dropped"):
    val result = parse("--color=never", "compile")
    assert(!result.sbtArguments.exists(_.contains("color")))
    assert(result.commandArguments.contains("compile"))

  test("-J-Xss4m is dropped"):
    val result = parse("-J-Xss4m", "compile")
    assert(!result.sbtArguments.contains("-J-Xss4m"))
    assert(result.commandArguments.contains("compile"))

  // -- Flags that should be preserved --

  test("-Dfoo=bar compile forwards -D property to sbtArguments"):
    val result = parse("-Dfoo=bar", "compile")
    assert(result.sbtArguments.exists(_.contains("-Dfoo=bar")))
    assert(result.commandArguments.contains("compile"))

  test("-bsp is still recognized"):
    val result = parse("-bsp")
    assert(result.bsp)

  test("--sbt-launch-jar is preserved"):
    val result = parse("--sbt-launch-jar", "/path/to/sbt-launch.jar", "compile")
    assert(result.sbtLaunchJar.contains("/path/to/sbt-launch.jar"))
    assert(result.commandArguments.contains("compile"))

  test("--sbt-script is preserved"):
    val result = parse("--sbt-script", "/usr/bin/sbt", "compile")
    assert(result.sbtScript == "/usr/bin/sbt")
    assert(result.commandArguments.contains("compile"))

  // -- Combined / integration --

  test("combined: -mem 10000 -Dfoo=bar compile test"):
    val result = parse("-mem", "10000", "-Dfoo=bar", "compile", "test")
    assert(!result.sbtArguments.contains("-mem"))
    assert(!result.sbtArguments.contains("10000"))
    assert(result.sbtArguments.exists(_.contains("-Dfoo=bar")))
    assert(result.commandArguments.contains("compile"))
    assert(result.commandArguments.contains("test"))

  test("combined: --client -batch -java-home /jdk --color=never -Dfoo=bar compile"):
    val result =
      parse("--client", "-batch", "-java-home", "/jdk", "--color=never", "-Dfoo=bar", "compile")
    assert(!result.sbtArguments.contains("--client"))
    assert(!result.sbtArguments.contains("-batch"))
    assert(!result.sbtArguments.contains("-java-home"))
    assert(!result.sbtArguments.contains("/jdk"))
    assert(!result.sbtArguments.exists(_.contains("color=never")))
    assert(result.sbtArguments.exists(_.contains("-Dfoo=bar")))
    assert(result.commandArguments.contains("compile"))
    assert(result.commandArguments.size == 1)

end NetworkClientParseArgsTest
