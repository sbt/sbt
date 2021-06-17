/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import scala.concurrent.duration._

// starts svr using server-test/buildserver and perform custom server tests
object BuildServerTest extends AbstractServerTest {
  override val testDirectory: String = "buildserver"

  test("build/initialize") { _ =>
    initializeRequest()
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"10"""") &&
      (s contains """"resourcesProvider":true""")
    })
  }

  test("workspace/buildTargets") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "11", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(processing("workspace/buildTargets"))
    assert {
      svr.waitForString(10.seconds) { s =>
        (s contains """"id":"11"""") &&
        (s contains """"displayName":"util"""")
      }
    }
  }

  test("buildTarget/sources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "12", "method": "buildTarget/sources", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/sources"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"12"""") &&
      (s contains "util/src/main/scala")
    })
  }

  test("buildTarget/compile") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "13", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/compile"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"13"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/scalacOptions") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "14", "method": "buildTarget/scalacOptions", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/scalacOptions"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"14"""") &&
      (s contains "scala-library-2.13.1.jar")
    })
  }

  test("workspace/reload") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "15", "method": "workspace/reload"}"""
    )
    assert(processing("workspace/reload"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"15"""") &&
      (s contains """"result":null""")
    })
  }

  test("buildTarget/scalaMainClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "16", "method": "buildTarget/scalaMainClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaMainClasses"))
    assert(svr.waitForString(30.seconds) { s =>
      (s contains """"id":"16"""") &&
      (s contains """"class":"main.Main"""")
    })
  }

  test("buildTarget/run") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "17", "method": "buildTarget/run", "params": {
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
      (s contains """"id":"17"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/scalaTestClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "18", "method": "buildTarget/scalaTestClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaTestClasses"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"18"""") &&
      (s contains """"tests.FailingTest"""") &&
      (s contains """"tests.PassingTest"""")
    })
  }

  test("buildTarget/test: run all tests") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "19", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"19"""") &&
      (s contains """"statusCode":2""")
    })
  }

  test("buildTarget/test: run one test class") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#runAndTest/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "20", "method": "buildTarget/test", "params": {
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
      (s contains """"id":"20"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/compile: report error") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#reportError/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "21", "method": "buildTarget/compile", "params": {
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
      s"""{ "jsonrpc": "2.0", "id": "22", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$x"}""") &&
      (s contains """"severity":2""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/resources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "23", "method": "buildTarget/resources", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/resources"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"23"""") && (s contains "util/src/main/resources/")
    })
  }

  private def initializeRequest(): Unit = {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "10", "method": "build/initialize",
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
