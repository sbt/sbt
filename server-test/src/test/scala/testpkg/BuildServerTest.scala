/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.bsp.SourcesResult

import java.io.File
import sbt.internal.bsp.WorkspaceBuildTargetsResult

import scala.concurrent.duration._

// starts svr using server-test/buildserver and perform custom server tests
object BuildServerTest extends AbstractServerTest {

  import sbt.internal.bsp.codec.JsonProtocol._

  override val testDirectory: String = "buildserver"

  test("build/initialize") { _ =>
    initializeRequest()
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"8"""") &&
      (s contains """"resourcesProvider":true""")
    })
  }

  test("workspace/buildTargets") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "16", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(processing("workspace/buildTargets"))
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val utilTarget = result.targets.find(_.displayName.contains("util")).get
    assert(utilTarget.id.uri.toString.endsWith("#util/Compile"))
    val buildServerBuildTarget =
      result.targets.find(_.displayName.contains("buildserver-build")).get
    assert(buildServerBuildTarget.id.uri.toString.endsWith("#buildserver-build"))
  }

  test("buildTarget/sources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "24", "method": "buildTarget/sources", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }
  test("buildTarget/sources SBT") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#buildserver-build"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "25", "method": "buildTarget/sources", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri).sorted
    val expectedSources = Vector(
      "build.sbt",
      "project/",
      "project/A.scala",
      "project/src/main/java",
      "project/src/main/scala-2",
      "project/src/main/scala-2.12",
      "project/src/main/scala-sbt-1.0",
      "project/src/main/scala/",
      "project/src/main/scala/B.scala",
      "project/target/scala-2.12/sbt-1.0/src_managed/main"
    ).map(rel => new File(svr.baseDirectory.getAbsoluteFile, rel).toURI).sorted
    assert(sources == expectedSources)
  }

  test("buildTarget/compile") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "32", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/compile"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"32"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/scalacOptions") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "40", "method": "buildTarget/scalacOptions", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/scalacOptions"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"40"""") &&
      (s contains "scala-library-2.13.1.jar")
    })
  }

  test("workspace/reload") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "48", "method": "workspace/reload"}"""
    )
    assert(processing("workspace/reload"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"48"""") &&
      (s contains """"result":null""")
    })
  }

  test("buildTarget/scalaMainClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "56", "method": "buildTarget/scalaMainClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaMainClasses"))
    assert(svr.waitForString(30.seconds) { s =>
      (s contains """"id":"56"""") &&
      (s contains """"class":"main.Main"""")
    })
  }

  test("buildTarget/run") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "64", "method": "buildTarget/run", "params": {
         |  "target": { "uri": "$x" },
         |  "dataKind": "scala-main-class",
         |  "data": { "class": "main.Main" }
         |} }""".stripMargin
    )
    assert(processing("buildTarget/run"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains "build/logMessage") &&
      (s contains """"message":"Hello World!"""")
    })
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"64"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/scalaTestClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "72", "method": "buildTarget/scalaTestClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaTestClasses"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"72"""") &&
      (s contains """"tests.FailingTest"""") &&
      (s contains """"tests.PassingTest"""")
    })
  }

  test("buildTarget/test: run all tests") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "80", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"80"""") &&
      (s contains """"statusCode":2""")
    })
  }

  test("buildTarget/test: run one test class") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "84", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$x" }],
         |  "dataKind": "scala-test",
         |  "data": {
         |    "testClasses": [
         |      {
         |        "target": { "uri": "$x" },
         |        "classes": ["tests.PassingTest"]
         |      }
         |    ]
         |  }
         |} }""".stripMargin
    )
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"84"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/compile: report error") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#reportError/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "88", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$x"}""") &&
      (s contains """"severity":1""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: report warning") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#reportWarning/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "90", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$x"}""") &&
      (s contains """"severity":2""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: respond error") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#respondError/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "92", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      s.contains(""""id":"92"""") &&
      s.contains(""""error"""") &&
      s.contains(""""code":-32603""") &&
      s.contains("custom message")
    })
  }

  test("buildTarget/resources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "96", "method": "buildTarget/resources", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/resources"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"96"""") && (s contains "util/src/main/resources/")
    })
  }

  private def initializeRequest(): Unit = {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "8", "method": "build/initialize",
        |  "params": {
        |    "displayName": "test client",
        |    "version": "1.0.0",
        |    "bspVersion": "2.0.0-M5",
        |    "rootUri": "file://root/",
        |    "capabilities": { "languageIds": ["scala"] }
        |  }
        |}""".stripMargin
    )
  }

  private def processing(method: String, debug: Boolean = false): Boolean = {
    svr.waitForString(10.seconds) { msg =>
      if (debug) println(msg)
      msg.contains("build/logMessage") &&
      msg.contains(s""""message":"Processing $method"""")
    }
  }
}
