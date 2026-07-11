/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbtw

object ConfigLoaderSpec extends verify.BasicTestSuite:
  test("tokenize keeps a double-quoted path together") {
    assert(
      ConfigLoader.tokenize("""-java-home "C:\Program Files\jdk"""") == Seq(
        "-java-home",
        "C:\\Program Files\\jdk"
      )
    )
  }

  test("tokenize keeps a single-quoted path together") {
    assert(ConfigLoader.tokenize("-sbt-dir '/Users/a dog'") == Seq("-sbt-dir", "/Users/a dog"))
  }

  test("tokenize splits on unquoted whitespace") {
    assert(ConfigLoader.tokenize("-mem 2048") == Seq("-mem", "2048"))
  }

  test("tokenize returns a single token for a bare flag") {
    assert(ConfigLoader.tokenize("-java-home") == Seq("-java-home"))
  }

  test("tokenize returns nothing for blank input") {
    assert(ConfigLoader.tokenize("   ") == Seq.empty)
  }
end ConfigLoaderSpec
