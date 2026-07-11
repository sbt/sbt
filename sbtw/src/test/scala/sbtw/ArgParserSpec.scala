/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbtw

object ArgParserSpec extends verify.BasicTestSuite:
  test("parse should accept --java-home") {
    val opts = ArgParser.parse(Array("--java-home", "C:\\jdk", "compile")).get
    assert(opts.javaHome == Some("C:\\jdk"))
    assert(opts.residual == Seq("compile"))
  }

  test("parse should detect new alongside --java-home") {
    val opts = ArgParser.parse(Array("--java-home", "C:\\jdk", "new")).get
    assert(opts.sbtNew)
  }
end ArgParserSpec
