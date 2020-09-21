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
      println(s)
      (s contains """"id":"10"""")
    })
  }

  test("workspace/buildTargets") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "11", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"11"""") &&
      (s contains """"displayName":"util"""")
    })
  }

  test("buildTarget/sources") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#util/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "12", "method": "buildTarget/sources", "params": {
        |  "targets": [{ "uri": "$x" }]
        |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
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
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
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
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"14"""") &&
      (s contains "scala-library-2.13.1.jar")
    })
  }

  test("workspace/reload") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "15", "method": "workspace/reload"}"""
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"15"""") &&
      (s contains """"result":null""")
    })
  }

  test("buildTarget/scalaMainClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#foo/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "16", "method": "buildTarget/scalaMainClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(30.seconds) { s =>
      println(s)
      (s contains """"id":"16"""") &&
      (s contains """"class":"foo.FooMain"""")
    })
  }

  test("buildTarget/run") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#foo/Compile"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "17", "method": "buildTarget/run", "params": {
         |  "target": { "uri": "$x" },
         |  "dataKind": "scala-main-class",
         |  "data": { "class": "foo.FooMain" }
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains "build/logMessage") &&
      (s contains """"message":"Hello World!"""")
    })
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"17"""") &&
      (s contains """"statusCode":1""")
    })
  }

  test("buildTarget/scalaTestClasses") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#foo/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "18", "method": "buildTarget/scalaTestClasses", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"18"""") &&
      (s contains """"classes":["foo.FailingTest","foo.FooTest"]""")
    })
  }

  test("buildTarget/test: run all tests") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#foo/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "19", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$x" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"19"""") &&
      (s contains """"statusCode":2""")
    })
  }

  test("buildTarget/test: run one test class") { _ =>
    val x = s"${svr.baseDirectory.getAbsoluteFile.toURI}#foo/Test"
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "20", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$x" }],
         |  "dataKind": "scala-test",
         |  "data": {
         |    "testClasses": [
         |      {
         |        "target": { "uri": "$x" },
         |        "classes": ["foo.FooTest"]
         |      }
         |    ]
         |  }
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      println(s)
      (s contains """"id":"20"""") &&
      (s contains """"statusCode":1""")
    })
  }

  def initializeRequest(): Unit = {
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
}
