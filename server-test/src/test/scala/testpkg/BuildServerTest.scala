/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.bsp._
import sbt.internal.langserver.ErrorCodes
import sbt.IO
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.protocol.codec.JsonRPCProtocol._
import sjsonnew.JsonWriter
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.File
import java.net.URI
import java.nio.file.Paths
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
    assert(!result.targets.exists(_.displayName.contains("badBuildTarget")))
  }

  test("buildTarget/sources") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(buildTargetSources(24, Seq(buildTarget, badBuildTarget)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }
  test("buildTarget/sources: base sources") { _ =>
    val buildTarget = buildTargetUri("buildserver", "Compile")
    svr.sendJsonRpc(buildTargetSources(25, Seq(buildTarget)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources
    val expectedSource = SourceItem(
      new File(svr.baseDirectory, "BaseSource.scala").toURI,
      SourceItemKind.File,
      generated = false
    )
    assert(sources.contains(expectedSource))
  }
  test("buildTarget/sources: sbt") { _ =>
    val x = new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#buildserver-build")
    svr.sendJsonRpc(buildTargetSources(26, Seq(x)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri).sorted
    val expectedSources = Vector(
      "build.sbt",
      "project/A.scala",
      "project/src/main/java",
      "project/src/main/scala-2",
      "project/src/main/scala-2.12",
      "project/src/main/scala-sbt-1.0",
      "project/src/main/scala/",
      "project/target/scala-2.12/sbt-1.0/src_managed/main"
    ).map(rel => new File(svr.baseDirectory.getAbsoluteFile, rel).toURI).sorted
    assert(sources == expectedSources)
  }
  test("buildTarget/compile") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "32", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/compile"))
    val res = svr.waitFor[BspCompileResult](10.seconds)
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile - reports compilation progress") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "33", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )

    // This doesn't always come back in 10s on CI.
    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskStart") &&
      s.contains(""""message":"Compiling runAndTest"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (15%)"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (100%)"""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/publishDiagnostics")
      s.contains(""""diagnostics":[]""")
    })

    assert(svr.waitForString(60.seconds) { s =>
      s.contains("build/taskFinish") &&
      s.contains(""""message":"Compiled runAndTest"""")
    })

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "34", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )

    assert(!svr.waitForString(30.seconds) { s =>
      s.contains("build/publishDiagnostics")
    }, "shouldn't send publishDiagnostics if there's no change in diagnostics")
  }

  test("buildTarget/scalacOptions") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "40", "method": "buildTarget/scalacOptions", "params": {
        |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
        |} }""".stripMargin
    )
    assert(processing("buildTarget/scalacOptions"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"40"""") &&
      (s contains "scala-library-2.13.8.jar")
    })
  }

  test("buildTarget/cleanCache") { _ =>
    def targetDir =
      Paths
        .get(
          svr.baseDirectory.getAbsoluteFile.toString,
          "run-and-test/target/scala-2.13/classes/main"
        )
        .toFile

    val buildTarget = buildTargetUri("runAndTest", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "43", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    svr.waitFor[BspCompileResult](10.seconds)
    assert(targetDir.list().contains("Main.class"))

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "44", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/cleanCache"))
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
    assert(targetDir.list().isEmpty)
  }

  test("buildTarget/cleanCache: rebuild project") { _ =>
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "45", "method": "workspace/buildTargets", "params": {} }"""
    )
    assert(processing("workspace/buildTargets"))
    val result = svr.waitFor[WorkspaceBuildTargetsResult](10.seconds)
    val allTargets = result.targets.map(_.id.uri)

    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "46", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [
         |    ${allTargets.map(uri => s"""{ "uri": "$uri" }""").mkString(",\n")}
         |  ]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/cleanCache"))
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
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

  test("workspace/reload: send diagnostic and respond with error") { _ =>
    // write an other-build.sbt file that does not compile
    val otherBuildFile = new File(svr.baseDirectory, "other-build.sbt")
    IO.write(
      otherBuildFile,
      """
        |val someSettings = Seq(
        |  scalacOptions ++= "-deprecation"
        |)
        |""".stripMargin
    )
    // reload
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "52", "method": "workspace/reload"}"""
    )
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""") &&
        s.contains(""""severity":1""") &&
        s.contains(""""reset":true""")
      }
    )
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(""""id":"52"""") &&
        s.contains(""""error"""") &&
        s.contains(s""""code":${ErrorCodes.InternalError}""") &&
        s.contains("Type error in expression")
      }
    )
    // fix the other-build.sbt file and reload again
    IO.write(
      otherBuildFile,
      """
        |val someSettings = Seq(
        |  scalacOptions += "-deprecation"
        |)
        |""".stripMargin
    )
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "52", "method": "workspace/reload"}"""
    )
    // assert received an empty diagnostic
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toPath.toUri}"}""") &&
        s.contains(""""diagnostics":[]""") &&
        s.contains(""""reset":true""")
      }
    )
    IO.delete(otherBuildFile)
  }

  test("buildTarget/scalaMainClasses") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "56", "method": "buildTarget/scalaMainClasses", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaMainClasses"))
    assert(svr.waitForString(30.seconds) { s =>
      (s contains """"id":"56"""") &&
      (s contains """"class":"main.Main"""")
    })
  }

  test("buildTarget/run") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "64", "method": "buildTarget/run", "params": {
         |  "target": { "uri": "$buildTarget" },
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

  test("buildTarget/jvmRunEnvironment") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    svr.sendJsonRpc(
      s"""|{ "jsonrpc": "2.0",
          |  "id": "97",
          |  "method": "buildTarget/jvmRunEnvironment",
          |  "params": { "targets": [{ "uri": "$buildTarget" }] }
          |}""".stripMargin
    )
    assert(processing("buildTarget/jvmRunEnvironment"))
    assert {
      svr.waitForString(10.seconds) { s =>
        (s contains """"id":"97"""") &&
        (s contains "jsoniter-scala-core_2.13-2.13.11.jar") && // compile dependency
        (s contains "\"jvmOptions\":[\"Xmx256M\"]") &&
        (s contains "\"environmentVariables\":{\"KEY\":\"VALUE\"}") &&
        (s contains "/buildserver/run-and-test/") // working directory
      }
    }
  }

  test("buildTarget/jvmTestEnvironment") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    svr.sendJsonRpc(
      s"""|{ "jsonrpc": "2.0", 
          |  "id": "98", 
          |  "method": "buildTarget/jvmTestEnvironment", 
          |  "params": { "targets": [{ "uri": "$buildTarget" }] } 
          |}""".stripMargin
    )
    assert(processing("buildTarget/jvmTestEnvironment"))
    assert {
      svr.waitForString(10.seconds) { s =>
        (s contains """"id":"98"""") &&
        // test depends on compile so it has dependencies from both
        (s contains "jsoniter-scala-core_2.13-2.13.11.jar") && // compile dependency
        (s contains "scalatest_2.13-3.0.8.jar") && // test dependency
        (s contains "\"jvmOptions\":[\"Xmx512M\"]") &&
        (s contains "\"environmentVariables\":{\"KEY_TEST\":\"VALUE_TEST\"}")
      }
    }
  }

  test("buildTarget/scalaTestClasses") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Test")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "72", "method": "buildTarget/scalaTestClasses", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/scalaTestClasses"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"72"""") &&
      (s contains """"tests.FailingTest"""") &&
      (s contains """"tests.PassingTest"""") &&
      (s contains """"framework":"ScalaTest"""")
    })
  }

  test("buildTarget/test: run all tests") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "80", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/test"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"80"""") &&
      (s contains """"statusCode":2""")
    })
  }

  test("buildTarget/test: run one test class") { _ =>
    val buildTarget = buildTargetUri("runAndTest", "Test")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "84", "method": "buildTarget/test", "params": {
         |  "targets": [{ "uri": "$buildTarget" }],
         |  "dataKind": "scala-test",
         |  "data": {
         |    "testClasses": [
         |      {
         |        "target": { "uri": "$buildTarget" },
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
    val buildTarget = buildTargetUri("reportError", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "88", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":1""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: report warning") { _ =>
    val buildTarget = buildTargetUri("reportWarning", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "90", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":2""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: respond error") { _ =>
    val buildTarget = buildTargetUri("respondError", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "92", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(svr.waitForString(10.seconds) { s =>
      s.contains(""""id":"92"""") &&
      s.contains(""""error"""") &&
      s.contains(s""""code":${ErrorCodes.InternalError}""") &&
      s.contains("custom message")
    })
  }

  test("buildTarget/resources") { _ =>
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "96", "method": "buildTarget/resources", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
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

  private def buildTargetSources(id: Int, buildTargets: Seq[URI]): String = {
    val targets = buildTargets.map(BuildTargetIdentifier.apply).toVector
    request(id, "buildTarget/sources", SourcesParams(targets))
  }

  private def request[T: JsonWriter](id: Int, method: String, params: T): String = {
    val request = JsonRpcRequestMessage("2.0", id.toString, method, Converter.toJson(params).get)
    val json = Converter.toJson(request).get
    CompactPrinter(json)
  }

  private def buildTargetUri(project: String, config: String): URI = {
    new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#$project/$config")
  }

  private def metaBuildTarget: String =
    s"${svr.baseDirectory.getAbsoluteFile.toURI}project/#buildserver-build/Compile"
}
