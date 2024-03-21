/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.internal.bsp.*
import sbt.internal.langserver.ErrorCodes
import sbt.IO
import sbt.internal.protocol.JsonRpcRequestMessage
import sbt.internal.protocol.codec.JsonRPCProtocol.*
import sjsonnew.JsonWriter
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter }

import java.io.File
import java.net.URI
import java.nio.file.Files
import scala.concurrent.duration.*

// starts svr using server-test/buildserver and perform custom server tests
class BuildServerTest extends AbstractServerTest {

  import sbt.internal.bsp.codec.JsonProtocol._

  override val testDirectory: String = "buildserver"

  test("build/initialize") {
    initializeRequest()
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"8"""") &&
      (s contains """"resourcesProvider":true""") &&
      (s contains """"outputPathsProvider":true""")
    })
  }

  test("workspace/buildTargets") {
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

  test("buildTarget/sources") {
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(buildTargetSources(24, Seq(buildTarget, badBuildTarget)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri)
    assert(sources.contains(new File(svr.baseDirectory, "util/src/main/scala").toURI))
  }
  test("buildTarget/sources: base sources") {
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

  test("buildTarget/sources: sbt") {
    val x = new URI(s"${svr.baseDirectory.getAbsoluteFile.toURI}#buildserver-build")
    svr.sendJsonRpc(buildTargetSources(26, Seq(x)))
    assert(processing("buildTarget/sources"))
    val s = svr.waitFor[SourcesResult](10.seconds)
    val sources = s.items.head.sources.map(_.uri).sorted
    val expectedSources = Vector(
      "build.sbt",
      "project/A.scala",
      "project/src/main/java",
      "project/src/main/scala-3",
      s"project/src/main/scala-sbt-${TestProperties.version}",
      "project/src/main/scala/",
      "target/out/jvm/scala-3.3.1/buildserver-build/src_managed/main"
    ).map(rel => new File(svr.baseDirectory.getAbsoluteFile, rel).toURI).sorted
    assert(sources == expectedSources)
  }

  test("buildTarget/compile") {
    val buildTarget = buildTargetUri("util", "Compile")

    compile(buildTarget, id = 32)

    assert(processing("buildTarget/compile"))
    val res = svr.waitFor[BspCompileResult](10.seconds)
    assert(res.statusCode == StatusCode.Success)
  }

  test("buildTarget/compile - reports compilation progress") {
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    compile(buildTarget, id = 33)
    // This doesn't always come back in 10s on CI.
    assert(svr.waitForString(20.seconds) { s =>
      s.contains("build/taskStart") &&
      s.contains(""""message":"Compiling runAndTest"""")
    })

    assert(svr.waitForString(20.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (15%)"""")
    })

    assert(svr.waitForString(20.seconds) { s =>
      s.contains("build/taskProgress") &&
      s.contains(""""message":"Compiling runAndTest (100%)"""")
    })

    assert(svr.waitForString(20.seconds) { s =>
      s.contains("build/publishDiagnostics")
      s.contains(""""diagnostics":[]""")
    })

    assert(svr.waitForString(20.seconds) { s =>
      s.contains("build/taskFinish") &&
      s.contains(""""message":"Compiled runAndTest"""")
    })
  }

  test(
    "buildTarget/compile [diagnostics] don't publish unnecessary for successful compilation case"
  ) {
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val mainFile = new File(svr.baseDirectory, "diagnostics/src/main/scala/Diagnostics.scala")

    compile(buildTarget, id = 33)

    assert(svr.waitForString(30.seconds) { s =>
      s.contains("build/taskFinish") &&
      s.contains(""""message":"Compiled diagnostics"""")
    })

    // introduce compile error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = ""
         |}""".stripMargin
    )

    reloadWorkspace(id = 55)
    compile(buildTarget, id = 66)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala") &&
        s.contains("\"message\":\"type mismatch")
      },
      "should send publishDiagnostics with type error for Main.scala"
    )

    // fix compilation error
    IO.write(
      mainFile,
      """|object Diagnostics {
         |  private val a: Int = 5
         |}""".stripMargin
    )

    reloadWorkspace(id = 77)
    compile(buildTarget, id = 88)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala") &&
        s.contains("\"diagnostics\":[]")
      },
      "should send publishDiagnostics with empty diagnostics"
    )

    // trigger no-op compilation
    compile(buildTarget, id = 99)

    assert(
      !svr.waitForString(20.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("Diagnostics.scala")
      },
      "shouldn't send publishDiagnostics if there's no change in diagnostics (were empty, are empty)"
    )
  }

  test("buildTarget/compile [diagnostics] clear stale warnings") {
    val buildTarget = buildTargetUri("diagnostics", "Compile")
    val testFile = new File(svr.baseDirectory, s"diagnostics/src/main/scala/PatternMatch.scala")

    compile(buildTarget, id = 33)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("PatternMatch.scala") &&
        s.contains(""""message":"match may not be exhaustive""")
      },
      "should send publishDiagnostics with type error for PatternMatch.scala"
    )

    IO.write(
      testFile,
      """|class PatternMatch {
         |  val opt: Option[Int] = None
         |  opt match {
         |    case Some(value) => ()
         |    case None => ()
         |  }
         |}
         |""".stripMargin
    )

    reloadWorkspace(id = 55)
    compile(buildTarget, id = 66)

    assert(
      svr.waitForString(30.seconds) { s =>
        s.contains("build/publishDiagnostics") &&
        s.contains("PatternMatch.scala") &&
        s.contains("\"diagnostics\":[]")
      },
      "should send publishDiagnostics with empty diagnostics"
    )

  }

  test("buildTarget/scalacOptions") {
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

  test("buildTarget/cleanCache") {
    def classFile = svr.baseDirectory.toPath.resolve(
      "target/out/jvm/scala-2.13.8/runandtest/classes/main/Main.class"
    )
    val buildTarget = buildTargetUri("runAndTest", "Compile")
    compile(buildTarget, id = 43)
    svr.waitFor[BspCompileResult](10.seconds)
    assert(Files.exists(classFile))
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "44", "method": "buildTarget/cleanCache", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/cleanCache"))
    val res = svr.waitFor[CleanCacheResult](10.seconds)
    assert(res.cleaned)
    assert(Files.notExists(classFile))
  }

  test("buildTarget/cleanCache: rebuild project") {
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

  test("workspace/reload") {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "48", "method": "workspace/reload"}"""
    )
    assert(processing("workspace/reload"))
    assert(svr.waitForString(10.seconds) { s =>
      (s contains """"id":"48"""") &&
      (s contains """"result":null""")
    })
  }

  test("workspace/reload: send diagnostic and respond with error") {
    // write an other-build.sbt file that does not compile
    val otherBuildFile = svr.baseDirectory.toPath.resolve("other-build.sbt")
    Files.writeString(
      otherBuildFile,
      """|val someSettings = Seq(
         |  scalacOptions ++= "-deprecation"
         |)
         |""".stripMargin
    )
    // reload
    reloadWorkspace(id = 52)
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toUri}"}""") &&
        s.contains(""""severity":1""") &&
        s.contains(""""reset":true""")
      }
    )
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(""""id":"52"""") &&
        s.contains(""""error"""") &&
        s.contains(s""""code":${ErrorCodes.InternalError}""") &&
        s.contains("No Append.Values[Seq[String], String] found")
      }
    )
    // fix the other-build.sbt file and reload again
    Files.writeString(
      otherBuildFile,
      """|val someSettings = Seq(
         |  scalacOptions += "-deprecation"
         |)
         |""".stripMargin
    )
    reloadWorkspace(id = 52)
    // assert received an empty diagnostic
    assert(
      svr.waitForString(10.seconds) { s =>
        s.contains(s""""buildTarget":{"uri":"$metaBuildTarget"}""") &&
        s.contains(s""""textDocument":{"uri":"${otherBuildFile.toUri}"}""") &&
        s.contains(""""diagnostics":[]""") &&
        s.contains(""""reset":true""")
      }
    )
    Files.delete(otherBuildFile)
  }

  test("buildTarget/scalaMainClasses") {
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

  test("buildTarget/run") {
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

  test("buildTarget/jvmRunEnvironment") {
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
        (s contains s""""workingDirectory":"${svr.baseDirectory.getAbsolutePath}/run-and-test"""")
      }
    }
  }

  test("buildTarget/jvmTestEnvironment") {
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
        (s contains "\"environmentVariables\":{\"KEY_TEST\":\"VALUE_TEST\"}") &&
        (s contains s""""workingDirectory":"${svr.baseDirectory.getAbsolutePath}/run-and-test"""")
      }
    }
  }

  test("buildTarget/scalaTestClasses") {
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

  test("buildTarget/test: run all tests") {
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

  test("buildTarget/test: run one test class") {
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

  test("buildTarget/compile: report error") {
    val buildTarget = buildTargetUri("reportError", "Compile")
    compile(buildTarget, id = 88)
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":1""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: report warning") {
    val buildTarget = buildTargetUri("reportWarning", "Compile")
    compile(buildTarget, id = 90)
    assert(svr.waitForString(10.seconds) { s =>
      (s contains s""""buildTarget":{"uri":"$buildTarget"}""") &&
      (s contains """"severity":2""") &&
      (s contains """"reset":true""")
    })
  }

  test("buildTarget/compile: respond error") {
    val buildTarget = buildTargetUri("respondError", "Compile")
    compile(buildTarget, id = 92)
    assert(svr.waitForString(10.seconds) { s =>
      s.contains(""""id":"92"""") &&
      s.contains(""""error"""") &&
      s.contains(s""""code":${ErrorCodes.InternalError}""") &&
      s.contains("custom message")
    })
  }

  test("buildTarget/resources") {
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

  test("buildTarget/outputPaths") {
    val buildTarget = buildTargetUri("util", "Compile")
    val badBuildTarget = buildTargetUri("badBuildTarget", "Compile")
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "97", "method": "buildTarget/outputPaths", "params": {
         |  "targets": [{ "uri": "$buildTarget" }, { "uri": "$badBuildTarget" }]
         |} }""".stripMargin
    )
    assert(processing("buildTarget/outputPaths"))
    val actualResult = svr.waitFor[OutputPathsResult](10.seconds)
    val expectedResult = OutputPathsResult(
      items = Vector(
        OutputPathsItem(
          target = BuildTargetIdentifier(buildTarget),
          outputPaths = Vector(
            OutputPathItem(
              uri = new File(svr.baseDirectory, "util/custom-target").toURI,
              kind = OutputPathItemKind.Directory
            )
          )
        )
      )
    )
    assert(actualResult == expectedResult)
  }

  private def initializeRequest(): Unit = {
    svr.sendJsonRpc(
      """{ "jsonrpc": "2.0", "id": "8", "method": "build/initialize",
        |  "params": {
        |    "displayName": "test client",
        |    "version": "1.0.0",
        |    "bspVersion": "2.1.0-M1",
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

  private def reloadWorkspace(id: Int): Unit =
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "workspace/reload"}"""
    )

  private def compile(buildTarget: URI, id: Int): Unit =
    compile(buildTarget.toString, id)

  private def compile(buildTarget: String, id: Int): Unit =
    svr.sendJsonRpc(
      s"""{ "jsonrpc": "2.0", "id": "$id", "method": "buildTarget/compile", "params": {
         |  "targets": [{ "uri": "$buildTarget" }]
         |} }""".stripMargin
    )

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
