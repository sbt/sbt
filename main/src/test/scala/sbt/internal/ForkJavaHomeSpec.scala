/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import sbt.Defaults

object ForkJavaHomeSpec extends verify.BasicTestSuite:
  private val homes = Map("temurin@17" -> File("/jdk/17"), "temurin@25" -> File("/jdk/25"))

  test("javaHome takes precedence over jdkVersion") {
    val explicit = File("/custom/jdk")
    val resolved = Defaults.resolveForkJavaHome(Some(explicit), Some("temurin@17"), homes)
    assert(resolved == Some(explicit))
  }

  test("jdkVersion resolves from the discovered java homes") {
    val resolved = Defaults.resolveForkJavaHome(None, Some("temurin@25"), homes)
    assert(resolved == Some(File("/jdk/25")))
  }

  test("returns None when neither javaHome nor jdkVersion is set") {
    assert(Defaults.resolveForkJavaHome(None, None, homes) == None)
  }

  test("unknown jdkVersion fails and lists the available keys") {
    val err =
      try
        Defaults.resolveForkJavaHome(None, Some("zulu@99"), homes)
        None
      catch case e: RuntimeException => Some(e)
    assert(err.exists(_.getMessage.contains("zulu@99")))
    assert(err.exists(_.getMessage.contains("temurin@17")))
  }
end ForkJavaHomeSpec
